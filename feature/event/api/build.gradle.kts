plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.foundation)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.navigation3.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.room.runtime)
            api(project(":feature:group:api"))
            api(project(":feature:task:api"))
            implementation(project(":core:utils"))
        }
    }
}
