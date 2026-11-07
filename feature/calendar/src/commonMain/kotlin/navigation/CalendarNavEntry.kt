package com.mkulikov.taskb.feature.calendar.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.calendar.api.CalendarRoute
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarScreen
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarStore
import com.mkulikov.taskb.feature.event.api.EventEditorRoute
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import org.koin.compose.getKoin

internal class CalendarNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<CalendarRoute> {
            val koin = getKoin()
            val store = rememberNavEntryResource(key = CalendarStore::class) {
                CalendarStore(
                    repository = koin.get(),
                    groups = koin.get(),
                    eventRepository = koin.get(),
                    weatherRepository = koin.get(),
                    holidayRepository = koin.get(),
                )
            }
            val state by store.state.collectAsState()
            CalendarScreen(
                state = state,
                onIntent = store::accept,
                onBack = navigator::back,
                onOpenTask = { taskId -> navigator.navigate(key = TaskEditorRoute.Edit(taskId = taskId)) },
                onOpenOccurrence = { task ->
                    navigator.navigate(key = TaskEditorRoute.Edit(taskId = task.id, occurrenceId = task.occurrenceId))
                },
                onCreateEvent = { date ->
                    navigator.navigate(
                        key = EventEditorRoute.Create(initialDate = date, rememberGroupSelection = true)
                    )
                },
                onOpenEvent = { event ->
                    navigator.navigate(
                        key = EventEditorRoute.Edit(
                            eventId = event.id,
                            occurrenceId = event.occurrenceId,
                        ),
                    )
                },
                onCreateTask = { date ->
                    navigator.navigate(
                        key = TaskEditorRoute.Create(initialDate = date, rememberGroupSelection = true)
                    )
                },
            )
        }
    }
}
