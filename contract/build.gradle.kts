// RAD-3: shared DTOs and endpoint constants for app and stub server.
// Content is owned by Fabian. Plain Kotlin/JVM, no Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // kotlin.time.Instant (stdlib) is still experimental; every DTO field
        // of that type needs this opt-in.
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}