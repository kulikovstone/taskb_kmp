package com.mkulikov.taskb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.AppRoot
import com.mkulikov.taskb.feature.hello.api.HelloRoute
import com.mkulikov.taskb.feature.home.api.HomeRoute
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.CancellationException

@Composable
internal fun TaskBApp(settings: IAppSettings) {
    var startDestination by remember(key1 = settings) { mutableStateOf<NavKey?>(value = null) }

    LaunchedEffect(key1 = settings) {
        var initialError: String? = null
        val showWelcomeScreen = try {
            settings.shouldShowWelcomeScreen()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            initialError = "Couldn't read settings."
            true
        }
        startDestination = if (showWelcomeScreen) HelloRoute(initialError = initialError) else HomeRoute
    }

    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val destination = startDestination
            if (destination == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AppRoot(startDestination = destination)
            }
        }
    }
}
