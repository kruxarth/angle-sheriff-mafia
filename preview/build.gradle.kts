plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    application
}

// Compiles the real app UI sources against Compose for Desktop, with small shims for the
// few Android-only calls (resources, SystemClock). Used to check and screenshot screens.
val appSources = "../app/src/main/java"

sourceSets.main {
    kotlin {
        srcDir(appSources)
        include(
            "com/angelsheriffmafia/app/ui/theme/**",
            "com/angelsheriffmafia/app/ui/components/**",
            "com/angelsheriffmafia/app/ui/screens/**",
            "com/angelsheriffmafia/app/AfterTurn.kt",
            "com/angelsheriffmafia/app/data/Setup.kt",
            "com/angelsheriffmafia/app/R.kt",
            "com/angelsheriffmafia/preview/**",
            "androidx/**",
            "android/**",
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":lan"))
    implementation(libs.zxing.core)
    implementation("org.jetbrains.compose.ui:ui-desktop:1.5.12")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:1.5.12")
    implementation("org.jetbrains.compose.material3:material3-desktop:1.5.12")
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.7.85.4")
}

application {
    mainClass.set("com.angelsheriffmafia.preview.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    systemProperty("java.awt.headless", "true")
}
