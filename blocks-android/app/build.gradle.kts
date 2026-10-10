plugins {
    id("com.android.application")
}

android {
    namespace = "uk.co.maviuk.retroblocks"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.co.maviuk.retroblocks"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
