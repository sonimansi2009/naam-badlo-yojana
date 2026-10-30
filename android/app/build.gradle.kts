plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.naambadlo.keypad"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.naambadlo.keypad"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "0.4.0"
    }

    // One fixed key, so every new build installs over the previous one.
    // If the env vars below are set (from GitHub secrets), that private key is
    // used instead of the shared one in android/keystore/.
    signingConfigs {
        create("fixed") {
            val envFile = System.getenv("NC_KEYSTORE_FILE")
            storeFile = if (envFile != null) file(envFile) else rootProject.file("keystore/naam-chupao.jks")
            storePassword = System.getenv("NC_KEYSTORE_PASSWORD") ?: "naamchupao"
            keyAlias = System.getenv("NC_KEY_ALIAS") ?: "naamchupao"
            keyPassword = System.getenv("NC_KEY_PASSWORD") ?: "naamchupao"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
