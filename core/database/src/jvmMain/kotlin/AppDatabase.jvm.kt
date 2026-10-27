package com.mkulikov.taskb.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import java.nio.file.Path

fun AppDatabase.Companion.open(file: Path = DatabasePaths.databaseFile()): AppDatabase {
    val absoluteFile = file.toAbsolutePath().normalize()
    Files.createDirectories(absoluteFile.parent)
    val builder = Room.databaseBuilder<AppDatabase>(
        name = absoluteFile.toString(),
        factory = AppDatabaseConstructor::initialize,
    )
    return open(builder, BundledSQLiteDriver())
}
