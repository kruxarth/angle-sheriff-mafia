plugins {
    // AGP and the Kotlin plugins share one classloader, so they're declared together here.
    // settings.gradle.kts swaps in build-jvm.gradle.kts for SDK-free builds (-PengineOnly=true).
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
