// Shell for RAD-12: Ktor stub server hosting stubs S1-S4 and the stub control API.
// Content and Ktor dependencies are owned by Fabian (RAD-14). Plain Kotlin/JVM, no Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}