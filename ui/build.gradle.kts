plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
        }
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
