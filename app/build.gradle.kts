plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nexvary.veil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nexvary.veil"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha01"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
}
