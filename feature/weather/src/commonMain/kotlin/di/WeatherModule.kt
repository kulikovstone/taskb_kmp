package com.mkulikov.taskb.feature.weather.di

import com.mkulikov.taskb.feature.weather.WeatherRepository
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import com.mkulikov.taskb.feature.weather.network.OpenMeteoApi
import com.mkulikov.taskb.feature.weather.network.WeatherClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

fun weatherModule(): Module = module {
    single { WeatherClient() } onClose { client -> client?.close() }
    single { OpenMeteoApi(client = get<WeatherClient>().client) }
    single<IWeatherRepository> { WeatherRepository(api = get(), locations = get()) }
}
