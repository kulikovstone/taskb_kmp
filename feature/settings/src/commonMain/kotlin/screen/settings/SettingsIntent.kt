package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation

internal sealed interface SettingsIntent {
    data class Search(val query: String) : SettingsIntent
    data class SelectLocation(val location: WeatherLocation) : SettingsIntent
    data object DisableWeather : SettingsIntent
    data object RetryLoad : SettingsIntent
}
