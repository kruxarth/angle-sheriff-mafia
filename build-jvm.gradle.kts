// Root build file for -PengineOnly=true: no Android Gradle plugin, so nothing comes from Google's Maven.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
