plugins {
    id("taskb.multiplatform.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.room.runtime)
            api(libs.navigation3.runtime)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)

        }
    }
}
