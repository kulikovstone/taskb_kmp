package com.mkulikov.taskb.feature.calendar.screen.calendar_day

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.component.CalendarDayContext
import com.mkulikov.taskb.feature.calendar.component.CalendarDayList
import com.mkulikov.taskb.feature.calendar.component.calendarSidebarTaskPadding
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarIntent
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarItem
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarState
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarSidebarDateLabel
import com.mkulikov.taskb.feature.calendar.screen.calendar.timedCalendarPreviewState
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.dialog.TaskBMessageDialog
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate

@Composable
internal fun CalendarDayScreen(
    date: LocalDate,
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenItem: (CalendarItem) -> Unit,
    onCreateTask: () -> Unit,
    onCreateEvent: () -> Unit,
    onOpenNoDate: () -> Unit,
) {
    val colors = TaskBTheme.colors
    Surface(modifier = Modifier.fillMaxSize().testTag(tag = "calendar-selected-day"), color = colors.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            TaskBTopAppBar(
                title = calendarSidebarDateLabel.format(value = date),
                onBack = onBack,
                titleSpacing = 0.dp,
                contentPadding = PaddingValues(horizontal = 8.dp),
                contentAlpha = 1f,
            ) {
                IconButton(onClick = onCreateTask, modifier = Modifier.size(size = 48.dp)) {
                    Icon(imageVector = TaskBIcons.AddTask, contentDescription = "New task", tint = colors.accent)
                }
                IconButton(onClick = onCreateEvent, modifier = Modifier.size(size = 48.dp)) {
                    Icon(imageVector = TaskBIcons.AddEvent, contentDescription = "New event", tint = colors.accent)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height = 48.dp)
                    .padding(paddingValues = calendarSidebarTaskPadding)
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(weight = 1f)) {
                    if (!state.isLoading && state.loadError == null) CalendarDayContext(state = state)
                }
                TextButton(
                    onClick = onOpenNoDate,
                    modifier = Modifier.width(width = 80.dp).height(height = 36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(text = "No date", style = TaskBTheme.typography.button, color = colors.accent)
                }
            }
            when {
                state.isLoading -> TaskBLoading(modifier = Modifier.weight(weight = 1f))
                state.loadError != null -> TaskBLoadError(
                    message = state.loadError,
                    onRetry = { onIntent(CalendarIntent.RetryLoad) },
                )
                else -> CalendarDayList(
                    state = state,
                    onIntent = onIntent,
                    onOpenTask = onOpenItem,
                    modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
                )
            }
        }
    }
    state.error?.let { message ->
        TaskBMessageDialog(title = "Couldn't update task", message = message) {
            onIntent(CalendarIntent.DismissError)
        }
    }
}

@Preview(name = "Calendar selected day", widthDp = 400, heightDp = 800)
@Composable
private fun CalendarDayScreenPreview() {
    val state = timedCalendarPreviewState()
    TaskBTheme(darkTheme = false) {
        CalendarDayScreen(
            date = requireNotNull(value = state.selectedDate),
            state = state,
            onIntent = {},
            onBack = {},
            onOpenItem = {},
            onCreateTask = {},
            onCreateEvent = {},
            onOpenNoDate = {},
        )
    }
}
