plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:database"))
            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":feature:task:api"))
            implementation(project(":ui"))
        }
    }
}
