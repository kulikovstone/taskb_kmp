package com.mkulikov.taskb.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import java.nio.file.Path

fun AppDatabase.Companion.open(file: Path = DatabasePaths.databaseFile()): AppDatabase {
    val absoluteFile = file.toAbsolutePath().normalize()
    Files.createDirectories(absoluteFile.parent)
    return open(absoluteFile.toString(), BundledSQLiteDriver())
}
