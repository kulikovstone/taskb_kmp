package com.mkulikov.taskb.feature.weather.api.repository

import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import kotlinx.coroutines.flow.Flow

interface IWeatherRepository {
    fun observeLocation(): Flow<WeatherLocation?>
    fun observeForecast(): Flow<WeatherForecast>
    suspend fun searchLocations(query: String): List<WeatherLocation>
    suspend fun setLocation(location: WeatherLocation?)
}
