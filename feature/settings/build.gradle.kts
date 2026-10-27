plugins {
    id("taskb.multiplatform.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:database"))
            implementation(project(":feature:settings:api"))
        }
    }
}
