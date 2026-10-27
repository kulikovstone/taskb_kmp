plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.navigation3.runtime)
            api(libs.navigation3.ui)
            api(libs.koin.core)
            api(libs.compose.ui)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.koin.compose)

        }
    }
}
