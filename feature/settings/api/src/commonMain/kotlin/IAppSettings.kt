package com.mkulikov.taskb.feature.settings.api

interface IAppSettings {
    suspend fun shouldShowWelcomeScreen(): Boolean

    suspend fun setShowWelcomeScreen(enabled: Boolean)
}
