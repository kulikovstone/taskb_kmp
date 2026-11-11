package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.settings.api.model.AppLocation

internal data class SettingsState(
    val showWelcomeScreen: Boolean? = null,
    val isUpdatingWelcomeScreen: Boolean = false,
    val welcomeScreenError: String? = null,
    val location: AppLocation? = null,
    val isLoading: Boolean = true,
    val query: String = "",
    val results: List<AppLocation> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)
