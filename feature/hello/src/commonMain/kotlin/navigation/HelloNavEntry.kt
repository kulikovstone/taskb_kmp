package com.mkulikov.taskb.feature.hello.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.hello.api.HelloRoute
import com.mkulikov.taskb.feature.hello.screen.hello.HelloScreen
import com.mkulikov.taskb.feature.calendar.api.CalendarRoute

internal class HelloNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<HelloRoute> { route ->
            HelloScreen(error = route.initialError, onContinue = { navigator.replaceAll(key = CalendarRoute) })
        }
    }
}
