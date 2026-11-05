package com.mkulikov.taskb.feature.calendar.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.calendar.api.CalendarRoute
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarScreen
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarStore
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import com.mkulikov.taskb.feature.event.api.EventEditorRoute
import org.koin.compose.getKoin

internal class CalendarNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<CalendarRoute> {
            val koin = getKoin()
            val store = rememberNavEntryResource(CalendarStore::class) { CalendarStore(koin.get(), koin.get(), eventRepository = koin.get()) }
            val state by store.state.collectAsState()
            CalendarScreen(
                state = state,
                onIntent = store::accept,
                onBack = navigator::back,
                onOpenTask = { navigator.navigate(TaskEditorRoute.Edit(it)) },
                onOpenOccurrence = { navigator.navigate(TaskEditorRoute.Edit(it.id, it.occurrenceId)) },
                onCreateEvent = { date -> navigator.navigate(EventEditorRoute.Create(initialDate = date, rememberGroupSelection = true)) },
                onOpenEvent = { navigator.navigate(EventEditorRoute.Edit(it.id, it.occurrenceId)) },
                onCreateTask = { date -> navigator.navigate(TaskEditorRoute.Create(initialDate = date, rememberGroupSelection = true)) },
            )
        }
    }
}
