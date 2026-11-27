plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.material)
            api(libs.compose.resources)
            implementation(libs.materialSymbols.outlined)
            implementation(libs.materialSymbols.outlinedFilled)

        }
    }
}

compose.resources {
    packageOfResClass = "com.mkulikov.taskb.ui.resources"
}
