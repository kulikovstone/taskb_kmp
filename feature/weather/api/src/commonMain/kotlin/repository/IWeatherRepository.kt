package com.mkulikov.taskb.feature.weather.api.repository

import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import kotlinx.coroutines.flow.Flow

interface IWeatherRepository {
    fun observeForecast(): Flow<WeatherForecast>
}
