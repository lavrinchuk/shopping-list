import com.github.spotbugs.snom.SpotBugsTask
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.spotbugs.base)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.example.shoppinglist"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ua.lavrinchuk.shoppinglist"
        minSdk = 26
        targetSdk = 37
        // CI injects a monotonically increasing Firebase build version.
        // Local Android Studio builds retain the original debug version.
        versionCode = providers.gradleProperty("ciVersionCode").map(String::toInt).getOrElse(2)
        versionName = providers.gradleProperty("ciVersionName").getOrElse("2.0")

        testInstrumentationRunner = "com.example.shoppinglist.ShoppingTestRunner"
    }

    buildTypes {
        getByName("debug") {
            enableUnitTestCoverage = true
            firebaseAppDistribution {
                appId = providers.gradleProperty("firebaseAppId")
                    .orElse(providers.environmentVariable("FIREBASE_APP_ID"))
                    .orElse("").get()
                artifactType = "APK"
                groups = "friends"
                // Generated in GitHub Actions; optional for local Firebase uploads.
                providers.environmentVariable("FIREBASE_RELEASE_NOTES_FILE").orNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { releaseNotesFile = it }
            }
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.recyclerview)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

// Always build a fresh APK before uploading it.
tasks.matching { it.name == "appDistributionUploadDebug" }.configureEach {
    dependsOn("assembleDebug")
}

// SpotBugs needs JARs/class roots rather than individual .class entries.
// Package our Android Java compilation output as an analyzable JAR.
val spotbugsDebugClassesJar = tasks.register<Jar>("spotbugsDebugClassesJar") {
    group = "verification"
    dependsOn("compileDebugJavaWithJavac")
    from(providers.provider {
        tasks.named<JavaCompile>("compileDebugJavaWithJavac")
            .get().destinationDirectory.get().asFile
    })
    archiveFileName.set("spotbugs-debug-classes.jar")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates/spotbugs"))
}

// Reuse AGP's actual Javac classpath: Android dependencies have already been
// transformed to consumable JARs. Avoid eagerly resolving variant configurations;
// AGP may create them after this build script is evaluated.
val debugJavaCompileClasspath = providers.provider {
    tasks.named<JavaCompile>("compileDebugJavaWithJavac").get().classpath
}

// Include runtime-only AndroidX dependencies for SpotBugs. Some referenced
// libraries (such as poolingcontainer and tracing) are implementation/runtime
// dependencies and are absent from JavaCompile's compile classpath.
// Use AGP's AAR-to-classes-JAR transform; never add these to the APK.
val spotbugsRuntimeClasspath = providers.provider {
    configurations.named("debugRuntimeClasspath").get().incoming.artifactView {
        attributes.attribute(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            "android-classes-jar"
        )
    }.files
}

// The Android SDK doesn't contain java.rmi.Remote. SpotBugs may encounter
// references to this JDK marker interface in dependency bytecode. Supply the
// *real* class from the CI JDK's java.rmi module for analysis only.
// A JDK runtime does not necessarily include a jmods/ directory. The jrt: /modules
// filesystem works for full JDKs and modular runtime images without .jmod files.
val spotbugsRmiClassesDir = layout.buildDirectory.dir("intermediates/spotbugs/rmi-classes")
val extractSpotbugsRmi = tasks.register("extractSpotbugsRmi") {
    group = "verification"
    description = "Extract the real java.rmi.Remote class from the running JDK image."
    outputs.dir(spotbugsRmiClassesDir)
    doLast {
        val filesystem = FileSystems.getFileSystem(URI.create("jrt:/"))
        val source = filesystem.getPath("/modules/java.rmi/java/rmi/Remote.class")
        check(Files.isRegularFile(source)) {
            "The current JVM does not contain java.rmi.Remote; check the CI JDK image"
        }
        val target = spotbugsRmiClassesDir.get()
            .file("java/rmi/Remote.class").asFile.toPath()
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
val spotbugsRmiJar = tasks.register<Jar>("spotbugsRmiJar") {
    group = "verification"
    description = "Package java.rmi.Remote for SpotBugs auxiliary classpath only."
    dependsOn(extractSpotbugsRmi)
    from(spotbugsRmiClassesDir)
    archiveFileName.set("spotbugs-java-rmi.jar")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates/spotbugs"))
}

tasks.register<SpotBugsTask>("spotbugsDebug") {
    group = "verification"
    description = "Analyze ShoppingList debug Java bytecode with SpotBugs."
    dependsOn(spotbugsDebugClassesJar, spotbugsRmiJar)
    val appJar = spotbugsDebugClassesJar.flatMap { it.archiveFile }
    classDirs.from(appJar)
    // Explicit input: the default classes FileTree scans only loose .class files.
    classes = files(appJar)
    sourceDirs.from("src/main/java")
    auxClassPaths.from(debugJavaCompileClasspath)
    auxClassPaths.from(spotbugsRuntimeClasspath)
    auxClassPaths.from(spotbugsRmiJar.flatMap { it.archiveFile })
    auxClassPaths.from(androidComponents.sdkComponents.bootClasspath)
    // Report existing findings without blocking adoption.
    ignoreFailures = true
    reports.create("html") {
        required = true
        outputLocation = layout.buildDirectory.file("reports/spotbugs/debug.html")
    }
    reports.create("xml") {
        required = true
        outputLocation = layout.buildDirectory.file("reports/spotbugs/debug.xml")
    }
}

// Format only maintained Java sources, including unit and instrumentation tests.
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("2.0.0").aosp().cleanup(false)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
