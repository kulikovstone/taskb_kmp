package com.mkulikov.taskb.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.di.databaseModule
import com.mkulikov.taskb.core.navigation.di.navigationModule
import com.mkulikov.taskb.feature.settings.di.settingsModule
import org.koin.dsl.module

internal fun appModule(databaseFactory: () -> AppDatabase) = module {
    includes(
        databaseModule(databaseFactory),
        settingsModule(),
        navigationModule(),
    )
}
