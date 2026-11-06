package com.mkulikov.taskb.feature.weather.di

import com.mkulikov.taskb.feature.weather.WeatherRepository
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import com.mkulikov.taskb.feature.weather.network.OpenMeteoApi
import com.mkulikov.taskb.feature.weather.network.WeatherClient
import org.koin.dsl.module
import org.koin.dsl.onClose

fun weatherModule() = module {
    single { WeatherClient() } onClose { it?.close() }
    single { OpenMeteoApi(get<WeatherClient>().client) }
    single<IWeatherRepository> { WeatherRepository(get(), get()) }
}
