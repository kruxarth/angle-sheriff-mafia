import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":engine"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

// A host with bot players that serves the real web page, for trying the browser client on a computer:
// ./gradlew -PengineOnly=true :lan:demo   then open http://localhost:8765
tasks.register<JavaExec>("demo") {
    group = "application"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.angelsheriffmafia.lan.DemoKt")
    workingDir = rootDir
    standardInput = System.`in`
}
