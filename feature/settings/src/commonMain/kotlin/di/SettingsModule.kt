package com.mkulikov.taskb.feature.settings.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.settings.AppSettings
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import org.koin.dsl.module
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.settings.navigation.SettingsNavEntry
import org.koin.core.qualifier.named

fun settingsModule() = module {
    single<INavEntry>(named<SettingsNavEntry>()) { SettingsNavEntry() }
    single { get<AppDatabase>().settingsDao() }
    single<IAppSettings> { AppSettings(inject()) }
}
