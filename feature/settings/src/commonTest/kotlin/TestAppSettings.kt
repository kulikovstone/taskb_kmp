package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.IAppSettings

internal class TestAppSettings : IAppSettings {
    private var showWelcomeScreen = true

    override suspend fun shouldShowWelcomeScreen(): Boolean = showWelcomeScreen

    override suspend fun setShowWelcomeScreen(enabled: Boolean) {
        showWelcomeScreen = enabled
    }
}
