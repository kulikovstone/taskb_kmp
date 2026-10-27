plugins {
    id("taskb.multiplatform.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)

            implementation(project(":core:database"))
            implementation(project(":feature:settings:api"))
        }
    }
}
