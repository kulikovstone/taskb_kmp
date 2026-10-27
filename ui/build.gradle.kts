plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.material)
            implementation(libs.materialSymbols.outlined)
            implementation(libs.materialSymbols.outlinedFilled)

        }
    }
}
