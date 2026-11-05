package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

internal val calendarSidebarTaskPadding = PaddingValues(horizontal = 24.dp)

@Composable
internal fun CalendarSidebar(state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (CalendarItem) -> Unit) {
    val tasks = state.sidebarItems
    val date = state.selectedDate
    val daily = state.sidebarDailyItems
    val events = state.sidebarEventItems
    val mainTasks = state.sidebarMainTaskItems
    val completed = state.sidebarCompletedItems
    val showTaskHeading = date != null || events.isNotEmpty()
    var dailyExpanded by remember(date) { mutableStateOf(true) }
    var eventsExpanded by remember(date) { mutableStateOf(true) }
    var tasksExpanded by remember(date) { mutableStateOf(true) }
    var completedExpanded by remember(date) { mutableStateOf(true) }
    val colors = TaskBTheme.colors
    val lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    val taskCount = tasks.count { !it.isEvent }
    val eventCount = tasks.count { it.isEvent }
    val count = buildList {
        if (taskCount > 0 || eventCount == 0) add("$taskCount ${if (taskCount == 1) "task" else "tasks"}")
        if (eventCount > 0) add("$eventCount ${if (eventCount == 1) "event" else "events"}")
    }.joinToString(" · ")
    val context = if (date == null) count else buildString {
        if (date == state.today) append("Today · ")
        append(calendarWeekdayLabel.format(date))
        append(" · ")
        append(count)
    }
    Column(Modifier.width(320.dp).fillMaxHeight().testTag("calendar-sidebar")) {
        Row(Modifier.padding(calendarSidebarTaskPadding).fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(date?.let(calendarSidebarDateLabel::format) ?: "No date", modifier = Modifier.weight(1f).testTag("calendar-sidebar-title"),
                style = TaskBTheme.typography.section.copy(lineHeightStyle = lineHeightStyle), color = colors.heading,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton({ onIntent(if (date == null) CalendarIntent.Today else CalendarIntent.ShowUnscheduled) },
                modifier = Modifier.width(if (date == null) 76.dp else 110.dp).height(36.dp).testTag("calendar-sidebar-toggle"),
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (date == null) "Today" else "No date", color = colors.accent, style = TaskBTheme.typography.label)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(context, modifier = Modifier.padding(calendarSidebarTaskPadding),
            style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle), color = colors.muted)
        Spacer(Modifier.height(16.dp))
        if (tasks.isEmpty()) Text(if (date == null) "No tasks or events without a date." else "No tasks or events for this day.",
            modifier = Modifier.padding(calendarSidebarTaskPadding), color = colors.muted, style = TaskBTheme.typography.body)
        val scroll = rememberLazyListState()
        LaunchedEffect(date) { scroll.scrollToItem(0) }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxHeight().testTag("calendar-sidebar-list")) {
                if (daily.isNotEmpty()) item(key = "daily-heading") {
                    CalendarSidebarSectionHeader("Daily · ${daily.size}", dailyExpanded, { dailyExpanded = !dailyExpanded },
                        Modifier.padding(calendarSidebarTaskPadding).testTag("calendar-sidebar-daily-heading"))
                    if (dailyExpanded) Spacer(Modifier.height(8.dp))
                }
                if (dailyExpanded) items(daily, key = { it.key }) { task ->
                    val enabled = (task.isEvent || task.id !in state.pendingCompletion) && state.pendingMove == null
                    CalendarTaskRow(task, state.groups.find { it.id == task.groupId }, { onOpenTask(task) },
                        { onIntent(CalendarIntent.ToggleCompleted(it, task.occurrenceId)) },
                        Modifier.testTag(if (task.isEvent) "calendar-${task.key}" else "calendar-task-${task.key}").calendarTaskDrag(task, drag, enabled,
                            sourceDate = date ?: task.dueDate) { id, target ->
                            onIntent(task.moveTo(target))
                        }, enabled = enabled, dragging = drag.item?.key == task.key, now = state.now,
                        contentPadding = calendarSidebarTaskPadding)
                }
                if (events.isNotEmpty()) item(key = "events-heading") {
                    if (daily.isNotEmpty()) Spacer(Modifier.height(16.dp))
                    CalendarSidebarSectionHeader("Events · ${events.size}", eventsExpanded, { eventsExpanded = !eventsExpanded },
                        Modifier.padding(calendarSidebarTaskPadding).testTag("calendar-sidebar-events-heading"))
                    if (eventsExpanded) Spacer(Modifier.height(4.dp))
                }
                if (eventsExpanded) items(events, key = { it.key }) { task ->
                    val enabled = (task.isEvent || task.id !in state.pendingCompletion) && state.pendingMove == null
                    CalendarTaskRow(task, state.groups.find { it.id == task.groupId }, { onOpenTask(task) }, {},
                        Modifier.testTag(if (task.isEvent) "calendar-${task.key}" else "calendar-task-${task.key}").calendarTaskDrag(task, drag, enabled,
                            sourceDate = date ?: task.dueDate) { id, target ->
                            onIntent(task.moveTo(target))
                        }, enabled = enabled, dragging = drag.item?.key == task.key, now = state.now,
                        contentPadding = calendarSidebarTaskPadding)
                    Spacer(Modifier.height(4.dp))
                }
                if (mainTasks.isNotEmpty() && showTaskHeading) item(key = "tasks-heading") {
                    if (events.isNotEmpty()) Spacer(Modifier.height(if (eventsExpanded) 12.dp else 16.dp))
                    else if (daily.isNotEmpty()) Spacer(Modifier.height(16.dp))
                    CalendarSidebarSectionHeader("Tasks · ${mainTasks.size}", tasksExpanded, { tasksExpanded = !tasksExpanded },
                        Modifier.padding(calendarSidebarTaskPadding).testTag("calendar-sidebar-tasks-heading"))
                    if (tasksExpanded) Spacer(Modifier.height(8.dp))
                }
                if (!showTaskHeading || tasksExpanded) items(mainTasks, key = { it.key }) { task ->
                    val enabled = (task.isEvent || task.id !in state.pendingCompletion) && state.pendingMove == null
                    CalendarTaskRow(task, state.groups.find { it.id == task.groupId }, { onOpenTask(task) },
                        { onIntent(CalendarIntent.ToggleCompleted(it, task.occurrenceId)) },
                        Modifier.testTag(if (task.isEvent) "calendar-${task.key}" else "calendar-task-${task.key}").calendarTaskDrag(task, drag, enabled,
                            sourceDate = date ?: task.dueDate) { id, target ->
                            onIntent(task.moveTo(target))
                        }, enabled = enabled, dragging = drag.item?.key == task.key,
                        contentPadding = calendarSidebarTaskPadding)
                }
                if (completed.isNotEmpty()) item(key = "completed-heading") {
                    if (mainTasks.isNotEmpty()) Spacer(Modifier.height(16.dp))
                    else if (events.isNotEmpty()) Spacer(Modifier.height(if (eventsExpanded) 12.dp else 16.dp))
                    else if (daily.isNotEmpty()) Spacer(Modifier.height(16.dp))
                    CalendarSidebarSectionHeader("Completed · ${completed.size}", completedExpanded, { completedExpanded = !completedExpanded },
                        Modifier.padding(calendarSidebarTaskPadding).testTag("calendar-sidebar-completed-heading"))
                    if (completedExpanded) Spacer(Modifier.height(8.dp))
                }
                if (completedExpanded) items(completed, key = { it.key }) { task ->
                    val enabled = (task.isEvent || task.id !in state.pendingCompletion) && state.pendingMove == null
                    CalendarTaskRow(task, state.groups.find { it.id == task.groupId }, { onOpenTask(task) },
                        { onIntent(CalendarIntent.ToggleCompleted(it, task.occurrenceId)) },
                        Modifier.testTag(if (task.isEvent) "calendar-${task.key}" else "calendar-task-${task.key}").calendarTaskDrag(task, drag, enabled,
                            sourceDate = date ?: task.dueDate) { id, target ->
                            onIntent(task.moveTo(target))
                        }, enabled = enabled, dragging = drag.item?.key == task.key, now = state.now,
                        contentPadding = calendarSidebarTaskPadding)
                }
            }
            if (scroll.canScrollForward || scroll.canScrollBackward) TaskBVerticalScrollbar(scroll, Modifier.fillMaxHeight())
        }
    }
}

@Composable
private fun CalendarSidebarSectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier.height(18.dp)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button,
                onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
            .semantics { heading(); stateDescription = if (expanded) "Expanded" else "Collapsed" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(TaskBIcons.ChevronRight, contentDescription = null, tint = TaskBTheme.colors.muted,
            modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f))
        Text(title, color = TaskBTheme.colors.muted,
            style = TaskBTheme.typography.label.copy(
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
