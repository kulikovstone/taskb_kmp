plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.material)
            implementation(libs.compose.materialIcons)

            implementation(project(":core:database"))
            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":feature:task:api"))
            implementation(project(":ui"))
        }
    }
}
