plugins {
    id("taskb.multiplatform.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        commonMain.dependencies {
            api(libs.room.runtime)
            api(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":feature:group:api"))
            implementation(project(":feature:settings:api"))
            implementation(project(":feature:item:api"))
            implementation(project(":feature:holiday:api"))
        }
        androidMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        jvmMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspJs", libs.room.compiler)
    add("kspWasmJs", libs.room.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
