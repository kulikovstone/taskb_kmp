plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:navigation"))
            implementation(project(":feature:home:api"))
            implementation(project(":feature:task:api"))
            implementation(project(":ui"))
        }
    }
}
