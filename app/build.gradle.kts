plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kaung139abc.hyperrecap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kaung139abc.hyperrecap"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
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
}
