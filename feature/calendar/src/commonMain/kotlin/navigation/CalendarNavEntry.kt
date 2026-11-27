package com.mkulikov.taskb.feature.calendar.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.calendar.api.CalendarRoute
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarItem
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarScreen
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarStore
import com.mkulikov.taskb.feature.group.api.GroupsRoute
import com.mkulikov.taskb.feature.item.api.EventEditorRoute
import com.mkulikov.taskb.feature.item.api.ItemCreationRoute
import com.mkulikov.taskb.feature.item.api.TaskEditorRoute
import com.mkulikov.taskb.feature.settings.api.SettingsRoute
import org.koin.compose.getKoin

internal class CalendarNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<CalendarRoute> {
            val store = rememberCalendarStore()
            val state by store.state.collectAsState()
            CalendarScreen(
                state = state,
                onIntent = store::accept,
                onOpenGroups = { navigator.navigate(key = GroupsRoute) },
                onOpenSettings = { navigator.navigate(key = SettingsRoute) },
                onCreateItem = { date, groupId ->
                    navigator.navigate(key = ItemCreationRoute(initialDate = date, initialGroupId = groupId))
                },
                onOpenItem = { item ->
                    when (item) {
                        is CalendarItem.TaskItem -> navigator.navigate(
                            key = TaskEditorRoute(
                                taskId = item.task.id,
                                occurrenceId = item.task.occurrenceId,
                            ),
                        )
                        is CalendarItem.EventItem -> navigator.navigate(
                            key = EventEditorRoute(
                                eventId = item.event.id,
                                occurrenceId = item.event.occurrenceId,
                            ),
                        )
                        is CalendarItem.HolidayItem -> Unit
                    }
                },
            )
        }
    }
}

@Composable
private fun rememberCalendarStore(): CalendarStore {
    val koin = getKoin()
    return rememberNavEntryResource(key = CalendarStore::class) {
        CalendarStore(
            repository = koin.get(),
            groups = koin.get(),
            eventRepository = koin.get(),
            weatherRepository = koin.get(),
            holidayRepository = koin.get(),
        )
    }
}
