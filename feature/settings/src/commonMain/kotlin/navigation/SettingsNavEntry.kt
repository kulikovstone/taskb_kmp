package com.mkulikov.taskb.feature.settings.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.settings.api.SettingsRoute
import com.mkulikov.taskb.feature.settings.screen.settings.SettingsScreen
import com.mkulikov.taskb.feature.settings.screen.settings.SettingsStore
import org.koin.compose.getKoin

internal class SettingsNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<SettingsRoute> {
            val koin = getKoin()
            val store = rememberNavEntryResource(key = SettingsStore::class) {
                SettingsStore(locations = koin.get(), groupSettings = koin.get())
            }
            val state by store.state.collectAsState()
            SettingsScreen(state = state, onIntent = store::accept, onBack = navigator::back)
        }
    }
}
