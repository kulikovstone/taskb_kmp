plugins { id("taskb.multiplatform.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":feature:settings:api"))
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            api(libs.room.runtime)
        }
    }
}
