package com.mkulikov.taskb.feature.hello.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.calendar.api.CalendarRoute
import com.mkulikov.taskb.feature.hello.api.HelloRoute
import com.mkulikov.taskb.feature.hello.screen.hello.HelloScreen
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class HelloNavEntry(private val settings: IAppSettings) : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<HelloRoute> { route ->
            val coroutineScope = rememberCoroutineScope()
            var continuing by remember(key1 = route) { mutableStateOf(value = false) }
            HelloScreen(error = route.initialError) {
                if (!continuing) {
                    continuing = true
                    coroutineScope.launch {
                        try {
                            settings.markWelcomeShownIfNewer(version = route.version)
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (_: Exception) {
                            // Allow access to the calendar and retry the welcome screen on the next launch.
                        }
                        navigator.replaceAll(key = CalendarRoute)
                    }
                }
            }
        }
    }
}
