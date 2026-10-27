plugins {
    id("taskb.multiplatform.library")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)

        }
    }
}
