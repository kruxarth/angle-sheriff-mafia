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
// The Android app needs the Android SDK. Build only the pure-Kotlin engine with -PengineOnly=true.
if (providers.gradleProperty("engineOnly").orNull != "true") {
    include(":app")
}
// Desktop harness that compiles the app's Compose screens and renders screenshots, no Android SDK needed.
// Run: ./gradlew -PengineOnly=true -Ppreview=true :preview:run
if (providers.gradleProperty("preview").orNull == "true") {
    include(":preview")
}
