package com.mkulikov.taskb.feature.settings

import androidx.room3.withWriteTransaction
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.settings.api.IItemGroupSettings
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.LAST_SELECTED_GROUP_ID
import com.mkulikov.taskb.feature.settings.api.database.REMEMBER_LAST_GROUP
import com.mkulikov.taskb.feature.settings.api.database.rememberLastGroupEnabled

internal class ItemGroupSettings(private val database: Lazy<AppDatabase>) : IItemGroupSettings {
    override suspend fun rememberLastGroupEnabled(): Boolean = database.value.settingsDao().rememberLastGroupEnabled()

    override suspend fun setRememberLastGroup(enabled: Boolean) {
        val db = database.value
        db.withWriteTransaction {
            val dao = db.settingsDao()
            dao.upsert(setting = ApplicationSetting(key = REMEMBER_LAST_GROUP, value = enabled.toString()))
            if (!enabled) dao.deleteValue(key = LAST_SELECTED_GROUP_ID)
        }
    }

    override suspend fun lastSelectedGroupId(): Int? = database.value.settingsDao()
        .getValue(key = LAST_SELECTED_GROUP_ID)
        ?.toIntOrNull()
}
