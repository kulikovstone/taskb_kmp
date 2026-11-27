plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.compose.material)
            implementation(libs.compose.resources)

            implementation(project(":core:navigation"))
            implementation(project(":feature:hello:api"))
            implementation(project(":feature:calendar:api"))
            implementation(project(":ui"))
        }
    }
}

compose.resources {
    packageOfResClass = "com.mkulikov.taskb.feature.hello.resources"
}
