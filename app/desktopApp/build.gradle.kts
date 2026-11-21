import java.io.File
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.gradle.work.DisableCachingByDefault

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

@DisableCachingByDefault(because = "Installs a launcher in the local user's application menu")
abstract class InstallLinuxDesktopEntry : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val launcher: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val icon: RegularFileProperty

    @get:OutputFile
    abstract val desktopEntry: RegularFileProperty

    @TaskAction
    fun install() {
        val executable = launcher.get().asFile
        check(executable.canExecute()) { "TaskB distribution is missing its executable launcher" }
        val iconFile = icon.get().asFile
        val destination = desktopEntry.get().asFile
        val escapedLauncher = executable.absolutePath
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("`", "\\`")
            .replace("$", "\\$")
            .replace("\\", "\\\\")
            .replace("%", "%%")
        destination.parentFile.mkdirs()
        destination.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=TaskB
            Comment=Tasks, events and long-term planning
            Exec="$escapedLauncher"
            TryExec=${executable.absolutePath}
            Icon=${iconFile.absolutePath}
            Terminal=false
            Categories=Office;Calendar;
            StartupWMClass=com-mkulikov-taskb-MainKt
            """.trimIndent() + "\n"
        )
        logger.lifecycle("Installed TaskB desktop entry: ${destination.absolutePath}")
    }
}

val isLinux = providers.systemProperty("os.name").get().startsWith("Linux")

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.mkulikov.taskb.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.get().metadata.installationPath.asFile.absolutePath

        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(true)
            optimize.set(false)
            // A long classpath can crash the Linux jpackage launcher in JDK 21 (JDK-8380085).
            joinOutputJars.set(isLinux)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "TaskB"
            packageVersion = project.version.toString()
            modules("java.instrument", "jdk.unsupported")

            windows {
                // Preserve the old mapping: version 1.38 becomes MSI version 1.0.38.
                packageVersion = project.version.toString().replace(".", ".0.")
                iconFile.set(project.file("packaging/icons/taskb.ico"))
            }
            macOS {
                iconFile.set(project.file("packaging/icons/taskb.icns"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/taskb.png"))
            }
        }
    }
}

val linuxAppImageDir = layout.buildDirectory.dir("compose/binaries/main/app")

if (isLinux) {
    // Compose registers its packaging tasks after project evaluation.
    afterEvaluate {
        tasks.named<AbstractJPackageTask>("createDistributable") {
            destinationDir.set(layout.buildDirectory.dir("compose/binaries/main-dev/app"))
        }
        tasks.named<AbstractJPackageTask>("createReleaseDistributable") {
            destinationDir.set(linuxAppImageDir)
        }
    }
}

tasks.register<InstallLinuxDesktopEntry>("installLinuxDesktopEntry") {
    group = "compose desktop"
    description = "Builds the TaskB release and installs its launcher and icon in the current user's application menu."
    dependsOn("createReleaseDistributable")
    onlyIf("Linux application menus are available only on Linux") { isLinux }

    launcher.set(linuxAppImageDir.map { it.file("TaskB/bin/TaskB") })
    icon.set(linuxAppImageDir.map { it.file("TaskB/lib/TaskB.png") })
    val dataDirectory = providers.environmentVariable("XDG_DATA_HOME")
        .filter { it.isNotBlank() }
        .orElse(providers.systemProperty("user.home").map { "$it/.local/share" })
    desktopEntry.set(layout.file(dataDirectory.map { File("$it/applications/com.mkulikov.taskb.desktop") }))
}
