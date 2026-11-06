package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation

internal data class SettingsState(
    val location: WeatherLocation? = null,
    val isLoading: Boolean = true,
    val query: String = "",
    val results: List<WeatherLocation> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)
