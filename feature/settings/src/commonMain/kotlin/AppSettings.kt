package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppSettings(private val settingsDao: Lazy<IAppSettingsDao>) : IAppSettings {
    // Lazy database creation can touch the file system; keep it off the UI thread.
    override suspend fun hasCompletedWelcome(): Boolean = withContext(Dispatchers.Default) {
        settingsDao.value.getValue(WELCOME_COMPLETED)?.toBooleanStrict() ?: false
    }

    override suspend fun completeWelcome() = withContext(Dispatchers.Default) {
        settingsDao.value.upsert(ApplicationSetting(WELCOME_COMPLETED, "true"))
    }

    private companion object {
        const val WELCOME_COMPLETED = "welcomeCompleted"
    }
}
