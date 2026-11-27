plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.compose.material)

            implementation(project(":core:navigation"))
            implementation(project(":feature:calendar:api"))
            implementation(project(":feature:group:api"))
            implementation(project(":feature:home:api"))
            implementation(project(":feature:item:api"))
            implementation(project(":feature:settings:api"))
            implementation(project(":ui"))
        }
    }
}
