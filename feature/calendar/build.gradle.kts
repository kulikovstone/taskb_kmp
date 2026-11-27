plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.compose.material)
            implementation(libs.compose.resources)

            implementation(project(":core:navigation"))
            implementation(project(":core:mvi"))
            implementation(project(":core:utils"))
            implementation(project(":feature:calendar:api"))
            implementation(project(":feature:item:api"))
            implementation(project(":feature:group:api"))
            implementation(project(":feature:weather:api"))
            implementation(project(":feature:holiday:api"))
            implementation(project(":feature:settings:api"))
            implementation(project(":ui"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "com.mkulikov.taskb.feature.calendar.resources"
}
