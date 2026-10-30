pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AngelSheriffMafia"

include(":engine")
// Playing on your own phones over the local network: host session, web server, client. Pure Kotlin.
include(":lan")
// The Android app needs the Android SDK. Build only the pure-Kotlin modules with -PengineOnly=true.
if (providers.gradleProperty("engineOnly").orNull != "true") {
    include(":app")
} else {
    rootProject.buildFileName = "build-jvm.gradle.kts"
}
// Desktop harness that compiles the app's Compose screens and renders screenshots, no Android SDK needed.
// Run: ./gradlew -PengineOnly=true -Ppreview=true :preview:run
if (providers.gradleProperty("preview").orNull == "true") {
    include(":preview")
}
