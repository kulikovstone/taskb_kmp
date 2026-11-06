package com.mkulikov.taskb.feature.weather.api.model

import kotlinx.datetime.LocalDate

data class DailyWeather(
    val date: LocalDate,
    val condition: WeatherCondition,
    val minimumCelsius: Double,
    val maximumCelsius: Double,
)
