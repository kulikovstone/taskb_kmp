package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppSettings(private val settingsDao: Lazy<IAppSettingsDao>) : IAppSettings {
    // Lazy database creation can touch the file system; keep it off the UI thread.
    override suspend fun shouldShowWelcomeScreen(): Boolean {
        return withContext(context = Dispatchers.Default) {
            settingsDao.value.getValue(key = SHOW_WELCOME_SCREEN)?.toBooleanStrict() ?: true
        }
    }

    override suspend fun setShowWelcomeScreen(enabled: Boolean) {
        withContext(context = Dispatchers.Default) {
            settingsDao.value.upsert(
                setting = ApplicationSetting(key = SHOW_WELCOME_SCREEN, value = enabled.toString()),
            )
        }
    }

    private companion object {
        const val SHOW_WELCOME_SCREEN = "showWelcomeScreen"
    }
}
