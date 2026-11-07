package com.mkulikov.taskb.core.database.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.DatabasePaths
import com.mkulikov.taskb.core.database.open
import java.nio.file.Path
import org.koin.core.module.Module

fun databaseModule(databaseFile: Path = DatabasePaths.databaseFile()): Module {
    return databaseModule {
        AppDatabase.open(file = databaseFile)
    }
}
