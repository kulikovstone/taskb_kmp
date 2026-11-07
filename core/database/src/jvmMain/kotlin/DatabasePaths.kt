package com.mkulikov.taskb.core.database

import java.nio.file.Path

object DatabasePaths {
    fun databaseFile(): Path {
        val home = Path.of(System.getProperty("user.home"))
        val directory = when {
            isWindows() -> environmentPath(name = "LOCALAPPDATA")?.resolve("TaskB")
                ?: home.resolve("AppData/Local/TaskB")

            isMac() -> home.resolve("Library/Application Support/TaskB")
            else -> (environmentPath(name = "XDG_DATA_HOME") ?: home.resolve(".local/share")).resolve("taskb")
        }
        return directory.resolve("taskb.db")
    }

    private fun isWindows() = System.getProperty("os.name").startsWith(prefix = "Windows", ignoreCase = true)

    private fun isMac() = System.getProperty("os.name").startsWith(prefix = "Mac", ignoreCase = true)

    private fun environmentPath(name: String): Path? {
        return System.getenv(
            name,
        )?.takeIf { environmentValue ->
            environmentValue.isNotBlank()
        }?.let { environmentValue -> Path.of(environmentValue) }?.takeIf(predicate = Path::isAbsolute)
    }
}
