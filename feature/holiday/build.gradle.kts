plugins { id("taskb.multiplatform.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":feature:holiday:api"))
            implementation(project(":core:database"))
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
