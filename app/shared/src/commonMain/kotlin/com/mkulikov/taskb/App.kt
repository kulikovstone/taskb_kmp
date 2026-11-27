package com.mkulikov.taskb

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.di.AppDependencyGraph
import com.mkulikov.taskb.feature.settings.api.IThemeSettings
import com.mkulikov.taskb.ui.theme.TaskBTheme
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject

@Composable expect fun App()

@Composable
internal fun App(databaseFactory: () -> AppDatabase, platformTheme: @Composable (Boolean) -> Unit = {}) {
    val graph = remember(key1 = databaseFactory) { AppDependencyGraph(databaseFactory = databaseFactory) }
    KoinIsolatedContext(context = graph.application) {
        val themeSettings = koinInject<IThemeSettings>()
        val mode by themeSettings.themeMode.collectAsState()
        val darkTheme = mode.isDark(systemIsDark = isSystemInDarkTheme())
        TaskBTheme(darkTheme = darkTheme) {
            platformTheme(darkTheme)
            TaskBApp(settings = koinInject(), themeSettings = themeSettings)
        }
    }
}
