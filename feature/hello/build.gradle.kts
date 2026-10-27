plugins {
    id("taskb.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:mvi"))
            implementation(project(":core:navigation"))
            implementation(project(":feature:hello:api"))
            implementation(project(":feature:home:api"))
            implementation(project(":feature:settings:api"))
            implementation(project(":ui"))
        }
    }
}
