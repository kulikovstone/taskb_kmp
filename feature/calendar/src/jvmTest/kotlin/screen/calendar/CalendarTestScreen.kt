package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.Composable
import com.mkulikov.taskb.feature.event.api.model.Event
import kotlinx.datetime.LocalDate

@Composable
internal fun CalendarTestScreen(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onCreateTask: (LocalDate?) -> Unit,
    onOpenEvent: (Event) -> Unit = {},
) {
    CalendarScreen(
        state = state.copy(taskList = state.taskList ?: state.undatedTasks),
        onIntent = onIntent,
        onBack = onBack,
        onOpenSettings = {},
        onCreateItem = { date, _ -> onCreateTask(date) },
        onOpenItem = { item ->
            when (item) {
                is CalendarItem.TaskItem -> onOpenTask(item.task.id)
                is CalendarItem.EventItem -> onOpenEvent(item.event)
                is CalendarItem.HolidayItem -> Unit
            }
        },
    )
}
