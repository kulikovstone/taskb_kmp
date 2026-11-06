package com.mkulikov.taskb.feature.settings.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.settings.AppLocationRepository
import com.mkulikov.taskb.feature.settings.AppSettings
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.settings.navigation.SettingsNavEntry
import com.mkulikov.taskb.feature.settings.network.LocationClient
import com.mkulikov.taskb.feature.settings.network.OpenMeteoGeocodingApi
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

fun settingsModule() = module {
    single<INavEntry>(named<SettingsNavEntry>()) { SettingsNavEntry() }
    single { get<AppDatabase>().settingsDao() }
    single<IAppSettings> { AppSettings(inject()) }
    single { LocationClient() } onClose { it?.close() }
    single { OpenMeteoGeocodingApi(get<LocationClient>().client) }
    single<IAppLocationRepository> { AppLocationRepository(get(), inject()) }
}
