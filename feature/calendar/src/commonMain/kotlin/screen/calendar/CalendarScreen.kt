package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.component.CalendarWorkspace
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate

private val calendarDesktopMinWidth = 840.dp

@Composable
internal fun CalendarScreen(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onCreateTask: (LocalDate?) -> Unit,
    onOpenOccurrence: ((Task) -> Unit)? = null,
    onCreateEvent: (LocalDate?) -> Unit = {},
    onOpenEvent: (Event) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
) {
    val openTask: (Task) -> Unit = { task -> onOpenOccurrence?.invoke(task) ?: onOpenTask(task.id) }
    val openItem: (CalendarItem) -> Unit = { item ->
        when (item) {
            is CalendarItem.TaskItem -> openTask(item.task)
            is CalendarItem.EventItem -> onOpenEvent(item.event)
            is CalendarItem.HolidayItem -> Unit
        }
    }
    CalendarWorkspace(state = state, onIntent = onIntent, minFeedbackWidth = calendarDesktopMinWidth) { drag ->
        if (maxWidth < calendarDesktopMinWidth) {
            CompactCalendar(
                state = state,
                onIntent = onIntent,
                onBack = onBack,
                onOpenDay = onOpenDay,
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                CalendarTopBar(
                    state = state,
                    onIntent = onIntent,
                    onBack = onBack,
                    onCreateTask = { onCreateTask(state.selectedDate) },
                    onCreateEvent = { onCreateEvent(state.selectedDate) },
                )
                when {
                    state.isLoading -> TaskBLoading(modifier = Modifier.weight(weight = 1f))
                    state.loadError != null -> TaskBLoadError(
                        message = state.loadError,
                        onRetry = { onIntent(CalendarIntent.RetryLoad) },
                    )
                    else -> Row(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                        CalendarSidebar(
                            state = state,
                            drag = drag,
                            onIntent = onIntent,
                            onOpenTask = openItem,
                        )
                        CalendarMonthGrid(
                            state = state,
                            drag = drag,
                            onIntent = onIntent,
                            onOpenTask = openItem,
                            modifier = Modifier.weight(weight = 1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Calendar", widthDp = 1440, heightDp = 900)
@Composable
private fun CalendarScreenPreview() {
    TaskBTheme(darkTheme = false) {
        CalendarScreen(
            state = calendarPreviewState(),
            onIntent = {},
            onBack = {},
            onOpenTask = {},
            onCreateTask = {},
        )
    }
}

@Preview(name = "Calendar with timed tasks", widthDp = 1440, heightDp = 900)
@Composable
private fun CalendarTimedScreenPreview() {
    TaskBTheme(darkTheme = false) {
        CalendarScreen(
            state = timedCalendarPreviewState(),
            onIntent = {},
            onBack = {},
            onOpenTask = {},
            onCreateTask = {},
        )
    }
}
