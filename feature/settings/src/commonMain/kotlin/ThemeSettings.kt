package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.IThemeSettings
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.settings.api.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class ThemeSettings(private val settings: Lazy<IAppSettingsDao>) : IThemeSettings {
    private val currentMode = MutableStateFlow(value = ThemeMode.System)
    private val mutex = Mutex()
    override val themeMode = currentMode.asStateFlow()

    override suspend fun loadThemeMode() {
        withContext(context = Dispatchers.Default) {
            mutex.withLock {
                val saved = settings.value.getValue(key = THEME_MODE)
                currentMode.value = ThemeMode.entries.firstOrNull { mode -> mode.name == saved } ?: ThemeMode.System
            }
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        mutex.withLock {
            // Finish publishing a committed change even if the settings screen closes during the write.
            withContext(context = NonCancellable) {
                settings.value.upsert(setting = ApplicationSetting(key = THEME_MODE, value = mode.name))
                currentMode.value = mode
            }
        }
    }

    private companion object {
        const val THEME_MODE = "themeMode"
    }
}
