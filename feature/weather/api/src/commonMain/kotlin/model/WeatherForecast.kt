package com.mkulikov.taskb.feature.weather.api.model

/** Daily values are in Celsius and dates belong to the selected city's time zone. */
data class WeatherForecast(
    val location: WeatherLocation? = null,
    val days: List<DailyWeather> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
