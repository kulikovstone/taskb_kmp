plugins {
    id("taskb.multiplatform.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.navigation3.runtime)
            api(project(":core:navigation"))
            api(libs.kotlinx.coroutines.core)
            api(libs.room.runtime)
            api(project(":ui"))
        }
    }
}
