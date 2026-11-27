package com.mkulikov.taskb.feature.settings.api.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface IAppSettingsDao {
    @Query(value = "SELECT value FROM application_settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query(value = "DELETE FROM application_settings WHERE `key` = :key")
    suspend fun deleteValue(key: String)

    @Upsert suspend fun upsert(setting: ApplicationSetting)
}
