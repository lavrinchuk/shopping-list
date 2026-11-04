import com.github.spotbugs.snom.SpotBugsTask
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.gradle.api.tasks.compile.JavaCompile

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

// SpotBugs does not automatically create Android variant tasks.
val spotbugsDebug = tasks.register<SpotBugsTask>("spotbugsDebug") {
    group = "verification"
    description = "Analyze ShoppingList debug Java bytecode with SpotBugs."
    dependsOn("compileDebugJavaWithJavac")
    sourceDirs.from("src/main/java")
    auxClassPaths.from(configurations.named("debugCompileClasspath"))
    auxClassPaths.from(android.bootClasspath)
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

tasks.withType<JavaCompile>()
    .matching { it.name == "compileDebugJavaWithJavac" }
    .configureEach {
        val debugCompile = this
        spotbugsDebug.configure {
            classDirs.from(debugCompile.destinationDirectory)
        }
    }
