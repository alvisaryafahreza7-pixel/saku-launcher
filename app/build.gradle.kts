plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.saku.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.saku.launcher"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "3.0.2"
    }

    // Kunci debug tetap supaya update tidak kena "package conflicts / signature mismatch"
    // antar GitHub Actions run. Ini bukan kunci Play Store.
    signingConfigs {
        create("stableDebug") {
            storeFile = file("saku-debug.p12")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"

            // Semua skema tanda tangan APK aktif. v3.1 ditambahkan lagi di CI
            // lewat apksigner + lineage (v3.1 hanya ada kalau kunci dirotasi).
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stableDebug")
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
