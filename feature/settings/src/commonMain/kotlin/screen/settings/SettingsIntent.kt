package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.settings.api.model.AppLocation

internal sealed interface SettingsIntent {
    data class Search(val query: String) : SettingsIntent
    data class SelectLocation(val location: AppLocation) : SettingsIntent
    data object ClearLocation : SettingsIntent
    data object RetryLoad : SettingsIntent
}
