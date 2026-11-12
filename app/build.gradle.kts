plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    namespace = "com.kaung139abc.hyperrecap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kaung139abc.hyperrecap"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            if (!releaseKeystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(files("libs/sherpa-onnx.aar"))
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("commons-io:commons-io:2.17.0")
    implementation("androidx.media3:media3-transformer:1.8.0")
    // MIT-licensed on-device llama.cpp inference runtime for downloadable GGUF models.
    implementation("com.llamatik:library:1.10.1")
}
