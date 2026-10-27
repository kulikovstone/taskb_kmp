plugins {
    id("taskb.multiplatform.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":feature:settings:api"))
            implementation(project(":feature:task:api"))
        }
    }
}
