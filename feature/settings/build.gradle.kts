plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.compose)
            implementation(libs.compose.material)
            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":feature:weather:api"))
            implementation(project(":ui"))

            implementation(project(":core:database"))
            implementation(project(":feature:settings:api"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
        }
    }
}
