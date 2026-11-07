package com.mkulikov.taskb.core.database.di

import com.mkulikov.taskb.core.database.AppDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

fun databaseModule(databaseFactory: () -> AppDatabase): Module = module {
    single { databaseFactory() } onClose { database -> database?.close() }
}
