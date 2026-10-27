package com.mkulikov.taskb.core.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun AppDatabase.Companion.open(context: Context): AppDatabase {
    val file = context.applicationContext.getDatabasePath("taskb.db")
    check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
        "Cannot create the database directory"
    }
    val builder = Room.databaseBuilder<AppDatabase>(
        name = file.absolutePath,
        factory = AppDatabaseConstructor::initialize,
    )
    return open(builder, BundledSQLiteDriver())
}
