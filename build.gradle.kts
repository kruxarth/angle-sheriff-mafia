plugins {
    // The Android Gradle plugin is applied in :app only, so the engine builds without the Android SDK.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
