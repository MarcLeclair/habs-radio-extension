plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.habsradiosync.tv"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    val appVersionCode = providers.gradleProperty("hrs.versionCode").map(String::toInt).get()
    val appVersionName = providers.gradleProperty("hrs.versionName").get()

    defaultConfig {
        applicationId = "io.github.habsradiosync.tv"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":apps:android-common-ui"))
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.10.0")
}
