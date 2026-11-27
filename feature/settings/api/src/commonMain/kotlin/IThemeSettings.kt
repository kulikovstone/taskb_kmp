package com.mkulikov.taskb.feature.settings.api

import com.mkulikov.taskb.feature.settings.api.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface IThemeSettings {
    val themeMode: StateFlow<ThemeMode>

    suspend fun loadThemeMode()

    suspend fun setThemeMode(mode: ThemeMode)
}
