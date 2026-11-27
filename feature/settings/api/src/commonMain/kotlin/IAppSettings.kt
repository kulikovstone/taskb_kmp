package com.mkulikov.taskb.feature.settings.api

interface IAppSettings {
    suspend fun shouldShowWelcomeScreen(version: String): Boolean

    /** Records a Get started confirmation and returns true if it advances the confirmed version. */
    suspend fun markWelcomeShownIfNewer(version: String): Boolean
}
