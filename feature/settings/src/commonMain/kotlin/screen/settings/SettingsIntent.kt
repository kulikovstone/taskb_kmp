package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.model.ThemeMode

internal sealed interface SettingsIntent {
    data class Search(val query: String) : SettingsIntent

    data class SelectLocation(val location: AppLocation) : SettingsIntent

    data object ClearLocation : SettingsIntent

    data class SetThemeMode(val mode: ThemeMode) : SettingsIntent

    data object LoadGroupSetting : SettingsIntent

    data class SetRememberLastGroup(val enabled: Boolean) : SettingsIntent

    data object RetryLoad : SettingsIntent
}
