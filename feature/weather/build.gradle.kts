plugins { id("taskb.multiplatform.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":feature:weather:api"))
            implementation(project(":feature:settings:api"))
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies { implementation(libs.ktor.client.cio) }
        androidMain.dependencies { implementation(libs.ktor.client.cio) }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
