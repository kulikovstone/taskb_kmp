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
internal fun CalendarSidebar(
    state: CalendarState,
    drag: CalendarDragState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenTask: (CalendarItem) -> Unit,
) {
    val tasks = state.sidebarItems
    val date = state.selectedDate
    val daily = state.sidebarDailyItems
    val events = state.sidebarEventItems
    val mainTasks = state.sidebarMainTaskItems
    val completed = state.sidebarCompletedItems
    val isDayCompleted = state.isSelectedDayCompleted
    val showTaskHeading = date != null || events.isNotEmpty()
    var dailyExpanded by remember(key1 = date) { mutableStateOf(value = true) }
    var eventsExpanded by remember(key1 = date) { mutableStateOf(value = true) }
    var tasksExpanded by remember(key1 = date) { mutableStateOf(value = true) }
    var completedExpanded by remember(key1 = date) { mutableStateOf(value = true) }
    val colors = TaskBTheme.colors
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
    val taskCount = tasks.count { item -> item is CalendarItem.TaskItem }
    val eventCount = tasks.count(predicate = CalendarItem::isEvent)
    val holidayCount = tasks.count(predicate = CalendarItem::isHoliday)
    val count = buildList {
        if (taskCount > 0 || eventCount == 0 && holidayCount == 0)
            add(element = "$taskCount ${if (taskCount == 1) "task" else "tasks"}")
        if (eventCount > 0) add(element = "$eventCount ${if (eventCount == 1) "event" else "events"}")
        if (holidayCount > 0) add(element = "$holidayCount ${if (holidayCount == 1) "holiday" else "holidays"}")
    }
        .joinToString(separator = " · ")
    val context = if (date == null) {
        count
    } else {
        buildString {
            if (date == state.today) append("Today · ")
            append(calendarWeekdayLabel.format(value = date))
            append(" · ")
            append(count)
        }
    }
    Column(modifier = Modifier.width(width = 320.dp).fillMaxHeight().testTag(tag = "calendar-sidebar")) {
        Row(
            modifier = Modifier
                .padding(paddingValues = calendarSidebarTaskPadding)
                .fillMaxWidth()
                .height(height = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = date?.let(block = calendarSidebarDateLabel::format) ?: "No date",
                modifier = Modifier.weight(weight = 1f).testTag(tag = "calendar-sidebar-title"),
                style = TaskBTheme.typography.section.copy(lineHeightStyle = lineHeightStyle),
                color = colors.heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = { onIntent(if (date == null) CalendarIntent.Today else CalendarIntent.ShowUnscheduled) },
                modifier = Modifier
                    .width(width = if (date == null) 76.dp else 110.dp)
                    .height(height = 36.dp)
                    .testTag(tag = "calendar-sidebar-toggle"),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = if (date == null) "Today" else "No date",
                    color = colors.accent,
                    style = TaskBTheme.typography.label,
                )
            }
        }
        Spacer(modifier = Modifier.height(height = 4.dp))
        Text(
            text = context,
            modifier = Modifier.padding(paddingValues = calendarSidebarTaskPadding),
            style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle),
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(height = 16.dp))
        if (tasks.isEmpty())
            Text(
                text = if (date == null) "No tasks or events without a date." else "No tasks or events for this day.",
                modifier = Modifier.padding(paddingValues = calendarSidebarTaskPadding),
                color = colors.muted,
                style = TaskBTheme.typography.body,
            )
        val scroll = rememberLazyListState()
        LaunchedEffect(key1 = date) { scroll.scrollToItem(index = 0) }
        LaunchedEffect(key1 = isDayCompleted) {
            if (isDayCompleted) scroll.scrollToItem(index = 0)
        }
        Row(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
            LazyColumn(
                state = scroll,
                modifier = Modifier.weight(weight = 1f).fillMaxHeight().testTag(tag = "calendar-sidebar-list"),
            ) {
                if (isDayCompleted)
                    item(key = "day-completed") {
                        CalendarDayCompleted(
                            modifier = Modifier
                                .padding(paddingValues = calendarSidebarTaskPadding)
                                .padding(bottom = 24.dp)
                        )
                    }
                if (daily.isNotEmpty())
                    item(key = "daily-heading") {
                        CalendarSidebarSectionHeader(
                            title = "Daily · ${daily.size}",
                            expanded = dailyExpanded,
                            onToggle = { dailyExpanded = !dailyExpanded },
                            modifier = Modifier
                                .padding(paddingValues = calendarSidebarTaskPadding)
                                .testTag(tag = "calendar-sidebar-daily-heading"),
                        )
                        if (dailyExpanded) Spacer(modifier = Modifier.height(height = 8.dp))
                    }
                if (dailyExpanded)
                    items(items = daily, key = { item -> item.key }) { task ->
                        val enabled = (task.isEvent || task.id !in state.pendingCompletion) && state.pendingMove == null
                        CalendarTaskRow(
                            task = task,
                            group = state.groups.find { group -> group.id == task.groupId },
                            onOpenTask = { onOpenTask(task) },
                            onToggle = { taskId ->
                                onIntent(CalendarIntent.ToggleCompleted(id = taskId, occurrenceId = task.occurrenceId))
                            },
                            modifier = Modifier
                                .testTag(
                                        tag = if (task.isEvent || task.isHoliday) {
                                            "calendar-${task.key}"
                                        } else {
                                            "calendar-task-${task.key}"
                                        }
                                    )
                                .calendarTaskDrag(
                                    task = task,
                                    dragState = drag,
                                    enabled = enabled,
                                    sourceDate = date ?: task.dueDate,
                                ) { id, target ->
                                    onIntent(task.moveTo(date = target))
                                },
                            enabled = enabled,
                            dragging = drag.item?.key == task.key,
                            now = state.now,
                            today = state.today,
                            contentPadding = calendarSidebarTaskPadding,
                        )
                    }
                if (events.isNotEmpty())
                    item(key = "events-heading") {
                        if (daily.isNotEmpty()) Spacer(modifier = Modifier.height(height = 16.dp))
                        CalendarSidebarSectionHeader(
                            title = "Events · ${events.size}",
                            expanded = eventsExpanded,
                            onToggle = { eventsExpanded = !eventsExpanded },
                            modifier = Modifier
                                .padding(paddingValues = calendarSidebarTaskPadding)
                                .testTag(tag = "calendar-sidebar-events-heading"),
                        )
                        if (eventsExpanded) Spacer(modifier = Modifier.height(height = 4.dp))
                    }
                if (eventsExpanded)
                    items(items = events, key = CalendarItem::key) { task ->
                        val enabled = !task.isHoliday &&
                            (task.isEvent || task.id !in state.pendingCompletion) &&
                            state.pendingMove == null
                        CalendarTaskRow(
                            task = task,
                            group = state.groups.find { group -> group.id == task.groupId },
                            onOpenTask = { onOpenTask(task) },
                            onToggle = {},
                            modifier = Modifier
                                .testTag(
                                        tag = if (task.isEvent || task.isHoliday) {
                                            "calendar-${task.key}"
                                        } else {
                                            "calendar-task-${task.key}"
                                        }
                                    )
                                .calendarTaskDrag(
                                    task = task,
                                    dragState = drag,
                                    enabled = enabled,
                                    sourceDate = date ?: task.dueDate,
                                ) { id, target ->
                                    onIntent(task.moveTo(date = target))
                                },
                            enabled = enabled,
                            dragging = drag.item?.key == task.key,
                            now = state.now,
                            today = state.today,
                            contentPadding = calendarSidebarTaskPadding,
                        )
                        Spacer(modifier = Modifier.height(height = 4.dp))
                    }
                if (mainTasks.isNotEmpty() && showTaskHeading)
                    item(key = "tasks-heading") {
                        if (events.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(height = if (eventsExpanded) 12.dp else 16.dp))
                        } else if (daily.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(height = 16.dp))
                        }
                        CalendarSidebarSectionHeader(
                            title = "Tasks · ${mainTasks.size}",
                            expanded = tasksExpanded,
                            onToggle = { tasksExpanded = !tasksExpanded },
                            modifier = Modifier
                                .padding(paddingValues = calendarSidebarTaskPadding)
                                .testTag(tag = "calendar-sidebar-tasks-heading"),
                        )
                        if (tasksExpanded) Spacer(modifier = Modifier.height(height = 8.dp))
                    }
                if (!showTaskHeading || tasksExpanded)
                    items(items = mainTasks, key = { item -> item.key }) { task ->
                        val enabled = (task.isEvent || task.id !in state.pendingCompletion) && state.pendingMove == null
                        CalendarTaskRow(
                            task = task,
                            group = state.groups.find { group -> group.id == task.groupId },
                            onOpenTask = { onOpenTask(task) },
                            onToggle = { taskId ->
                                onIntent(CalendarIntent.ToggleCompleted(id = taskId, occurrenceId = task.occurrenceId))
                            },
                            modifier = Modifier
                                .testTag(
                                        tag = if (task.isEvent || task.isHoliday) {
                                            "calendar-${task.key}"
                                        } else {
                                            "calendar-task-${task.key}"
                                        }
                                    )
                                .calendarTaskDrag(
                                    task = task,
                                    dragState = drag,
                                    enabled = enabled,
                                    sourceDate = date ?: task.dueDate,
                                ) { id, target ->
                                    onIntent(task.moveTo(date = target))
                                },
                            enabled = enabled,
                            dragging = drag.item?.key == task.key,
                            now = state.now,
                            today = state.today,
                            contentPadding = calendarSidebarTaskPadding,
                        )
                    }
                if (completed.isNotEmpty())
                    item(key = "completed-heading") {
                        if (mainTasks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(height = 16.dp))
                        } else if (events.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(height = if (eventsExpanded) 12.dp else 16.dp))
                        } else if (daily.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(height = 16.dp))
                        }
                        CalendarSidebarSectionHeader(
                            title = "Completed · ${completed.size}",
                            expanded = completedExpanded,
                            onToggle = { completedExpanded = !completedExpanded },
                            modifier = Modifier
                                .padding(paddingValues = calendarSidebarTaskPadding)
                                .testTag(tag = "calendar-sidebar-completed-heading"),
                        )
                        if (completedExpanded) Spacer(modifier = Modifier.height(height = 8.dp))
                    }
                if (completedExpanded)
                    items(items = completed, key = { item -> item.key }) { task ->
                        val enabled = (task.isEvent || task.id !in state.pendingCompletion) && state.pendingMove == null
                        CalendarTaskRow(
                            task = task,
                            group = state.groups.find { group -> group.id == task.groupId },
                            onOpenTask = { onOpenTask(task) },
                            onToggle = { taskId ->
                                onIntent(CalendarIntent.ToggleCompleted(id = taskId, occurrenceId = task.occurrenceId))
                            },
                            modifier = Modifier
                                .testTag(
                                        tag = if (task.isEvent || task.isHoliday) {
                                            "calendar-${task.key}"
                                        } else {
                                            "calendar-task-${task.key}"
                                        }
                                    )
                                .calendarTaskDrag(
                                    task = task,
                                    dragState = drag,
                                    enabled = enabled,
                                    sourceDate = date ?: task.dueDate,
                                ) { id, target ->
                                    onIntent(task.moveTo(date = target))
                                },
                            enabled = enabled,
                            dragging = drag.item?.key == task.key,
                            now = state.now,
                            today = state.today,
                            contentPadding = calendarSidebarTaskPadding,
                        )
                    }
            }
            if (scroll.canScrollForward || scroll.canScrollBackward)
                TaskBVerticalScrollbar(state = scroll, modifier = Modifier.fillMaxHeight())
        }
    }
}

@Composable
private fun CalendarSidebarSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(height = 18.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = if (expanded) "Collapse" else "Expand",
                onClick = onToggle,
            )
            .semantics {
                heading()
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Icon(
            imageVector = TaskBIcons.ChevronRight,
            contentDescription = null,
            tint = TaskBTheme.colors.muted,
            modifier = Modifier.size(size = 16.dp).rotate(degrees = if (expanded) 90f else 0f),
        )
        Text(
            text = title,
            color = TaskBTheme.colors.muted,
            style = TaskBTheme.typography.label.copy(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                )
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
