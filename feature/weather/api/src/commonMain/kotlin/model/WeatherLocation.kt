package com.mkulikov.taskb.feature.weather.api.model

import kotlinx.datetime.TimeZone

data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
) {
    init {
        require(name.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        TimeZone.of(timeZone)
    }
}
