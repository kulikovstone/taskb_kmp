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
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarIntent
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarItem
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarScreen
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarStore
import com.mkulikov.taskb.feature.calendar.screen.calendar_day.CalendarDayScreen
import com.mkulikov.taskb.feature.event.api.EventEditorRoute
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import com.mkulikov.taskb.feature.task.api.TaskRoute
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.LocalDate
import org.koin.compose.getKoin

internal class CalendarNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<CalendarRoute> {
            val store = rememberCalendarStore()
            val state by store.state.collectAsState()
            CalendarScreen(
                state = state,
                onIntent = store::accept,
                onBack = navigator::back,
                onOpenTask = { taskId -> navigator.navigate(key = TaskEditorRoute.Edit(taskId = taskId)) },
                onOpenOccurrence = navigator::openCalendarTask,
                onCreateEvent = navigator::createCalendarEvent,
                onOpenEvent = navigator::openCalendarEvent,
                onCreateTask = navigator::createCalendarTask,
                onOpenDay = { date ->
                    store.accept(intent = CalendarIntent.SelectDay(date = date))
                    navigator.navigate(key = CalendarDayRoute(date = date))
                },
            )
        }
        scope.entry<CalendarDayRoute> { route ->
            val store = rememberCalendarStore(initialDate = route.date)
            val state by store.state.collectAsState()
            CalendarDayScreen(
                date = route.date,
                state = state,
                onIntent = store::accept,
                onBack = navigator::back,
                onOpenItem = { item ->
                    when (item) {
                        is CalendarItem.TaskItem -> navigator.openCalendarTask(task = item.task)
                        is CalendarItem.EventItem -> navigator.openCalendarEvent(event = item.event)
                        is CalendarItem.HolidayItem -> Unit
                    }
                },
                onCreateTask = { navigator.createCalendarTask(date = route.date) },
                onCreateEvent = { navigator.createCalendarEvent(date = route.date) },
                onOpenNoDate = { navigator.navigate(key = TaskRoute.NoDate) },
            )
        }
    }
}

@Composable
private fun rememberCalendarStore(initialDate: LocalDate? = null): CalendarStore {
    val koin = getKoin()
    return rememberNavEntryResource(key = CalendarStore::class) {
        CalendarStore(
            repository = koin.get(),
            groups = koin.get(),
            eventRepository = koin.get(),
            weatherRepository = if (initialDate == null) koin.get() else null,
            holidayRepository = koin.get(),
            initialDate = initialDate,
        )
    }
}

private fun IAppNavigator.openCalendarTask(task: Task) {
    navigate(key = TaskEditorRoute.Edit(taskId = task.id, occurrenceId = task.occurrenceId))
}

private fun IAppNavigator.openCalendarEvent(event: Event) {
    navigate(key = EventEditorRoute.Edit(eventId = event.id, occurrenceId = event.occurrenceId))
}

private fun IAppNavigator.createCalendarTask(date: LocalDate?) {
    navigate(key = TaskEditorRoute.Create(initialDate = date, rememberGroupSelection = true))
}

private fun IAppNavigator.createCalendarEvent(date: LocalDate?) {
    navigate(key = EventEditorRoute.Create(initialDate = date, rememberGroupSelection = true))
}
