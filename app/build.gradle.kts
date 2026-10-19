import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    jacoco
}

android {
    namespace = "com.iu.radioapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.iu.radioapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default: loopback, reached through "adb reverse tcp:8080 tcp:8080" - on
        // Android 17 the emulator alias 10.0.2.2 needs the local network
        // permission, loopback does not. Override with -PapiBaseUrl=... for
        // another host (10.0.2.2 on older emulators, a LAN address for a device
        // without adb); see README.md (RAD-21).
        val apiBaseUrl = providers.gradleProperty("apiBaseUrl").getOrElse("http://127.0.0.1:8080/")
        // Checked here: an empty value or a quote would otherwise surface as a broken URL at runtime or a javac error.
        require(Regex("""https?://[^\s"\\]+""").matches(apiBaseUrl)) {
            "apiBaseUrl must be an http(s) URL such as http://127.0.0.1:8080/, was '$apiBaseUrl'"
        }
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.trimEnd('/')}/\"")
    }

    buildTypes {
        // Coverage of unit and instrumented tests, merged by debugCoverageReport (RAD-20, E78).
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }
}

ksp {
    // Room writes the schema of every version here. The JSON is committed so a
    // later migration can be diffed against a recorded schema (decision E20).
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // kotlin.time.Instant (stdlib) is still experimental; every domain field
        // of that type needs this opt-in. Mirrors contract/build.gradle.kts.
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Generated or non-product classes left out of the coverage report; reasons in E79.
val coverageExcludes = listOf(
    // Hilt / Dagger
    "**/Hilt_*", "**/HiltWrapper_*", "**/*_HiltModule*", "**/*_Factory*", "**/*Module_*Factory*",
    "**/*_MembersInjector*", "**/*_GeneratedInjector*", "**/*_ComponentTreeDeps*",
    "**/*_HiltComponents*", "**/*_AssistedFactory*", "dagger/hilt/**", "hilt_aggregated_deps/**",
    // Room
    "**/*_Impl*",
    // Compose compiler
    "**/ComposableSingletons*",
    // kotlinx.serialization (navigation routes)
    "**/*\$\$serializer*",
    // Android
    "**/BuildConfig*", "**/R.class", "**/R\$*", "**/Manifest*",
) + fileTree("src/debug/java").matching { include("**/*.kt") }.files.flatMap { file ->
    // Debug source set (latency measurement, E70): derived from the files so new ones drop out too.
    val base = file.relativeTo(file("src/debug/java")).invariantSeparatorsPath.removeSuffix(".kt")
    listOf("$base.class", "$base\$*", "${base}Kt.class", "${base}Kt\$*")
}

/** JacocoReport plus the inputs AGP's variant API can fill with the compiled classes. */
abstract class CoverageReport : JacocoReport() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirs: ListProperty<Directory>
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val report = tasks.register<CoverageReport>("debugCoverageReport") {
            group = "verification"
            description = "Merged line and branch coverage of the unit and instrumented tests, as HTML and XML."
            dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")
            executionData.from(
                layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"),
                layout.buildDirectory.dir("outputs/code_coverage/debugAndroidTest/connected")
                    .map { dir -> dir.asFileTree.matching { include("**/*.ec") } },
            )
            classDirectories.from(files(classDirs).asFileTree.matching { exclude(coverageExcludes) })
            sourceDirectories.from("src/main/java")
            reports {
                html.required = true
                xml.required = true
            }
        }
        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
            .use(report)
            .toGet(ScopedArtifact.CLASSES, CoverageReport::classJars, CoverageReport::classDirs)
    }
}

dependencies {
    // Shared DTOs and endpoint constants (RAD-3)
    implementation(project(":contract"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Android core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Background delivery
    implementation(libs.androidx.work.runtime.ktx)

    // HTTP client against S1-S4 (RAD-14)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // WebSocket channel (RAD-15)
    implementation(libs.ktor.client.websockets)

    // Kotlin libraries
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
    // Real WS server to test RatingWebSocketClient against - MockEngine has no
    // WebSocket support (KTOR-537), a real (if throwaway, in-process) one is
    // the only way to test a dropped/reconnecting connection.
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.websockets)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.work.testing)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}