import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("taskb.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    android {
        androidResources {
            enable = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("compose-uiToolingPreview").get())
        }
    }
}

dependencies {
    add("androidRuntimeClasspath", libs.findLibrary("compose-uiTooling").get())
}
