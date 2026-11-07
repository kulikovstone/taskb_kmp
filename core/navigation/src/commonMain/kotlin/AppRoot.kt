package com.mkulikov.taskb.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.getKoin
import org.koin.compose.koinInject

@Composable
fun AppRoot(startDestination: NavKey, modifier: Modifier = Modifier) {
    val navigator = koinInject<AppNavigator>()
    val koin = getKoin()
    val entries = remember(key1 = koin) { koin.getAll<INavEntry>() }

    remember(key1 = navigator, key2 = startDestination) {
        navigator.setStartDestination(key = startDestination)
    }

    NavDisplay(
        backStack = navigator.backStack,
        onBack = navigator::back,
        modifier = modifier.fillMaxSize(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberEntryResourceNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entries.forEach { entry -> entry.register(scope = this, navigator = navigator) }
        },
    )
}
