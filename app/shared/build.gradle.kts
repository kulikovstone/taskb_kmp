import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

abstract class GenerateAppVersion : DefaultTask() {
    @get:Input
    abstract val applicationVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val source = outputDirectory.get().file("com/mkulikov/taskb/AppVersion.kt").asFile
        source.parentFile.mkdirs()
        source.writeText(
            "package com.mkulikov.taskb\n\n" +
                "internal object AppVersion { const val value = \"${applicationVersion.get()}\" }\n"
        )
    }
}

val generateAppVersion = tasks.register<GenerateAppVersion>("generateAppVersion") {
    applicationVersion.set(project.version.toString())
    outputDirectory.set(layout.buildDirectory.dir("generated/appVersion/kotlin"))
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    jvm()
    js {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    android {
        namespace = "com.mkulikov.taskb.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppVersion)
        }
        commonMain.dependencies {
            implementation(project(":core:database"))
            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":feature:calendar"))
            implementation(project(":feature:calendar:api"))
            implementation(project(":feature:event"))
            implementation(project(":feature:group"))
            implementation(project(":feature:hello"))
            implementation(project(":feature:hello:api"))
            implementation(project(":feature:home"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:settings:api"))
            implementation(project(":feature:task"))
            implementation(project(":feature:weather"))
            implementation(project(":feature:holiday"))
            implementation(project(":ui"))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(project(":feature:group:api"))
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
