import com.github.spotbugs.snom.SpotBugsTask
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.spotbugs.base)
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

// Resolve Android AAR libraries to classes JARs using AGP's artifact transform.
// Passing raw AARs makes SpotBugs miss AndroidX and third-party classes.
val spotbugsDebugDependencyJars = configurations.named("debugCompileClasspath").get()
    .incoming.artifactView {
        attributes.attribute(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            "android-classes-jar"
        )
    }.files

tasks.register<SpotBugsTask>("spotbugsDebug") {
    group = "verification"
    description = "Analyze ShoppingList debug Java bytecode with SpotBugs."
    dependsOn(spotbugsDebugClassesJar)
    val appJar = spotbugsDebugClassesJar.flatMap { it.archiveFile }
    classDirs.from(appJar)
    // Explicit input: the default classes FileTree scans only loose .class files.
    classes = files(appJar)
    sourceDirs.from("src/main/java")
    auxClassPaths.from(spotbugsDebugDependencyJars)
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
