import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.firebase.appdistribution)
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
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "com.example.shoppinglist.ShoppingTestRunner"
    }

    buildTypes {
        getByName("debug") {
            firebaseAppDistribution {
                appId = providers.gradleProperty("firebaseAppId")
                    .orElse(providers.environmentVariable("FIREBASE_APP_ID"))
                    .orElse("").get()
                artifactType = "APK"
                groups = "friends"
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
