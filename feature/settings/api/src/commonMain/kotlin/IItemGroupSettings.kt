package com.mkulikov.taskb.feature.settings.api

interface IItemGroupSettings {
    suspend fun rememberLastGroupEnabled(): Boolean

    suspend fun setRememberLastGroup(enabled: Boolean)

    suspend fun lastSelectedGroupId(): Int?
}
