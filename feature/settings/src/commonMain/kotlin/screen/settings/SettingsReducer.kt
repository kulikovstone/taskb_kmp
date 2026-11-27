package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.settings.api.model.AppLocation

internal sealed interface SettingsMessage {
    data class Location(val value: AppLocation?) : SettingsMessage

    data object Loading : SettingsMessage

    data class Query(val value: String) : SettingsMessage

    data class SearchResult(
        val query: String,
        val results: List<AppLocation>,
        val failed: Boolean = false,
    ) : SettingsMessage

    data object Saving : SettingsMessage

    data class Saved(val failed: Boolean) : SettingsMessage

    data object UpdatingGroupSetting : SettingsMessage

    data class GroupSetting(val enabled: Boolean) : SettingsMessage

    data object GroupSettingFailed : SettingsMessage

    data object LoadFailed : SettingsMessage
}

internal fun reduceSettings(state: SettingsState, message: SettingsMessage): SettingsState {
    return when (message) {
        is SettingsMessage.Location -> state.copy(location = message.value, isLoading = false, error = null)
        SettingsMessage.Loading -> state.copy(isLoading = true, error = null)
        SettingsMessage.LoadFailed -> state.copy(isLoading = false, error = "Couldn't load settings. Please try again.")
        is SettingsMessage.Query -> state.copy(
            query = message.value,
            results = emptyList(),
            isSearching = message.value.trim().length >= 2,
            hasSearched = false,
            error = null,
        )
        is SettingsMessage.SearchResult -> if (message.query != state.query) {
            state
        } else {
            state.copy(
                results = message.results,
                isSearching = false,
                hasSearched = !message.failed,
                error = if (message.failed) "Couldn't search cities. Check your connection and try again." else null,
            )
        }
        SettingsMessage.UpdatingGroupSetting -> state.copy(isUpdatingGroupSetting = true, groupSettingError = false)
        is SettingsMessage.GroupSetting -> state.copy(
            rememberLastGroup = message.enabled,
            isUpdatingGroupSetting = false,
        )
        SettingsMessage.GroupSettingFailed -> state.copy(isUpdatingGroupSetting = false, groupSettingError = true)
        SettingsMessage.Saving -> state.copy(isSaving = true, error = null)
        is SettingsMessage.Saved -> if (message.failed) {
            state.copy(isSaving = false, error = "Couldn't save the city. Please try again.")
        } else {
            state.copy(
                isSaving = false,
                query = "",
                results = emptyList(),
                hasSearched = false,
                isSearching = false,
                error = null,
            )
        }
    }
}
