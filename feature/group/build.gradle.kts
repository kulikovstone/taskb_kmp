plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.material)

            implementation(project(":core:database"))
            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":core:utils"))
            implementation(project(":feature:group:api"))
            implementation(project(":ui"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(project(":feature:event:api"))
            implementation(project(":feature:task:api"))
        }
    }
}
