package com.mkulikov.taskb.feature.settings

import androidx.room3.withWriteTransaction
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppSettings(private val database: Lazy<AppDatabase>) : IAppSettings {
    override suspend fun shouldShowWelcomeScreen(version: String): Boolean {
        return withContext(context = Dispatchers.Default) {
            needsConfirmation(dao = database.value.settingsDao(), version = version)
        }
    }

    override suspend fun markWelcomeShownIfNewer(version: String): Boolean {
        return withContext(context = Dispatchers.Default) {
            val db = database.value
            db.withWriteTransaction {
                val dao = db.settingsDao()
                if (!needsConfirmation(dao = dao, version = version)) return@withWriteTransaction false
                dao.upsert(setting = ApplicationSetting(key = CONFIRMED_WELCOME_VERSION, value = version))
                true
            }
        }
    }

    private suspend fun needsConfirmation(dao: IAppSettingsDao, version: String): Boolean {
        val previous = dao.getValue(key = CONFIRMED_WELCOME_VERSION) ?: return true
        val (major, revision) = version.split('.')
        val (previousMajor, previousRevision) = previous.split('.')
        val majorOrder = versionPartOrder.compare(major, previousMajor)
        return if (majorOrder == 0) versionPartOrder.compare(revision, previousRevision) > 0 else majorOrder > 0
    }

    private companion object {
        const val CONFIRMED_WELCOME_VERSION = "confirmedWelcomeVersion"

        // The versioning plugin emits two decimal parts without leading zeros or a fixed digit limit.
        val versionPartOrder = compareBy<String>(String::length, { digits -> digits })
    }
}
