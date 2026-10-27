plugins {
    id("taskb.multiplatform.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.navigation3.runtime)

        }
    }
}
