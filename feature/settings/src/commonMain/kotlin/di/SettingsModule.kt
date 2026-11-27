package com.mkulikov.taskb.feature.settings.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.settings.AppLocationRepository
import com.mkulikov.taskb.feature.settings.AppSettings
import com.mkulikov.taskb.feature.settings.ItemGroupSettings
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.IItemGroupSettings
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.settings.navigation.SettingsNavEntry
import com.mkulikov.taskb.feature.settings.network.LocationClient
import com.mkulikov.taskb.feature.settings.network.OpenMeteoGeocodingApi
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

fun settingsModule(): Module = module {
    single<INavEntry>(qualifier = named<SettingsNavEntry>()) { SettingsNavEntry() }
    single { get<AppDatabase>().settingsDao() }
    single<IItemGroupSettings> { ItemGroupSettings(database = inject()) }
    single<IAppSettings> { AppSettings(database = inject()) }
    single { LocationClient() } onClose { client -> client?.close() }
    single { OpenMeteoGeocodingApi(client = get<LocationClient>().client) }
    single<IAppLocationRepository> { AppLocationRepository(api = get(), settings = inject()) }
}
