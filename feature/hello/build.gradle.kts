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

            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":feature:hello:api"))
            implementation(project(":feature:home:api"))
            implementation(project(":feature:settings:api"))
            implementation(project(":ui"))
        }
    }
}
