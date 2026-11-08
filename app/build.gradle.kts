plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aminu.photodream"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aminu.photodream"
        minSdk = 26          // Android 8.0+ (phones, tablets, Android TV / Google TV)
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// No external libraries needed - everything uses the built-in Android framework.
dependencies {
}
