package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun CalendarSidebar(state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (Task) -> Unit) {
    val tasks = state.sidebarTasks
    val date = state.selectedDate
    val colors = TaskBTheme.colors
    val lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    val count = "${tasks.size} ${if (tasks.size == 1) "task" else "tasks"}"
    val context = if (date == null) count else buildString {
        if (date == state.today) append("Today · ")
        append(calendarWeekdayLabel.format(date))
        append(" · ")
        append(count)
    }
    Column(Modifier.width(320.dp).fillMaxHeight().testTag("calendar-sidebar").padding(24.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(date?.let(calendarSidebarDateLabel::format) ?: "Unscheduled", modifier = Modifier.weight(1f).testTag("calendar-sidebar-title"),
                style = TaskBTheme.typography.section.copy(lineHeightStyle = lineHeightStyle), color = colors.heading,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton({ onIntent(if (date == null) CalendarIntent.Today else CalendarIntent.ShowUnscheduled) },
                modifier = Modifier.width(if (date == null) 76.dp else 110.dp).height(36.dp).testTag("calendar-sidebar-toggle"),
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (date == null) "Today" else "Unscheduled", color = colors.accent, style = TaskBTheme.typography.label)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(context, style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle), color = colors.muted)
        Spacer(Modifier.height(16.dp))
        if (tasks.isEmpty()) Text(if (date == null) "No unscheduled tasks." else "No tasks for this day.",
            color = colors.muted, style = TaskBTheme.typography.body)
        val scroll = rememberLazyListState()
        LaunchedEffect(date) { scroll.scrollToItem(0) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("calendar-sidebar-list")) {
                items(tasks, key = { it.key }) { task ->
                    val enabled = task.id !in state.pendingCompletion && state.pendingMove == null
                    CalendarTaskRow(task, state.groups.find { it.id == task.groupId }, { onOpenTask(task) },
                        { onIntent(CalendarIntent.ToggleCompleted(it, task.occurrenceId)) },
                        Modifier.testTag("calendar-task-${task.key}").calendarTaskDrag(task, drag, enabled) { id, target ->
                            onIntent(CalendarIntent.MoveTask(id, target, task.occurrenceId))
                        }, enabled = enabled, dragging = drag.task?.key == task.key)
                }
            }
            TaskBVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}
