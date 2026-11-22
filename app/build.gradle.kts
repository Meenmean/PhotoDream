plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aminu.photodream"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aminu.photodream"
        minSdk = 26          // Android 8.0+ (phones, tablets, Android TV / Google TV)
        targetSdk = 36       // Google Play requirement for new apps from 31 Aug 2026
        versionCode = 4
        versionName = "1.3"
    }

    packaging {
        resources {
            // Duplicate licence/manifest files inside the network-share libraries
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"
            )
        }
    }

    // Used by the GitHub build (see .github/workflows/build.yml). Building in Android Studio
    // with "Generate Signed App Bundle or APK" ignores this and uses the key you pick there.
    val ciKeystore = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        create("release") {
            if (ciKeystore != null) {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ciKeystore != null) signingConfig = signingConfigs.getByName("release")
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

dependencies {
    // Reads Windows / Mac / NAS shared folders (SMB2/3)
    implementation("com.hierynomus:smbj:0.15.0")
    // smbj publishes these as runtime-only; listing them lets the compiler see every class it needs
    implementation("com.hierynomus:asn-one:0.6.0")
    implementation("net.engio:mbassador:1.3.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    implementation("org.slf4j:slf4j-api:2.0.18")
}
