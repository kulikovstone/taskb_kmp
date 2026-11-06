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
            implementation(project(":feature:task:api"))
            implementation(project(":feature:event:api"))
            implementation(project(":feature:group:api"))
            implementation(project(":feature:weather:api"))
            implementation(project(":feature:holiday:api"))
            implementation(project(":ui"))
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

compose.resources {
    packageOfResClass = "com.mkulikov.taskb.feature.calendar.resources"
}
