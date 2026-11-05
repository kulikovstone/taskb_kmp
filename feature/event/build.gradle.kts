plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
        }
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.material)
            implementation(libs.compose.material3)
            implementation(libs.kotlinx.datetime)

            implementation(project(":core:database"))
            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":core:utils"))
            implementation(project(":feature:group:api"))
            implementation(project(":feature:event:api"))
            implementation(project(":ui"))
        }
    }
}
