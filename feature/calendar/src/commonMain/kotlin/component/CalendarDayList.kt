package com.mkulikov.taskb.feature.calendar.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
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
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarDayCompleted
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarDragState
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarIntent
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarItem
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarState
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarTaskRow
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarTaskDrag
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarWeekdayLabel
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

internal val calendarSidebarTaskPadding = PaddingValues(horizontal = 24.dp)

internal fun calendarItemCount(taskCount: Int, eventCount: Int, holidayCount: Int = 0): String = buildList {
    if (taskCount > 0 || eventCount == 0 && holidayCount == 0)
        add(element = "$taskCount ${if (taskCount == 1) "task" else "tasks"}")
    if (eventCount > 0) add(element = "$eventCount ${if (eventCount == 1) "event" else "events"}")
    if (holidayCount > 0) add(element = "$holidayCount ${if (holidayCount == 1) "holiday" else "holidays"}")
}.joinToString(separator = " · ")

@Composable
internal fun CalendarDayContext(state: CalendarState, modifier: Modifier = Modifier) {
    val tasks = state.sidebarItems
    val date = state.selectedDate
    val taskCount = tasks.count { item -> item is CalendarItem.TaskItem }
    val eventCount = tasks.count(predicate = CalendarItem::isEvent)
    val holidayCount = tasks.count(predicate = CalendarItem::isHoliday)
    val count = calendarItemCount(taskCount = taskCount, eventCount = eventCount, holidayCount = holidayCount)
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
    Text(
        text = context,
        modifier = modifier,
        style = TaskBTheme.typography.small.copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        color = TaskBTheme.colors.muted,
    )
}

@Composable
internal fun CalendarDayList(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenTask: (CalendarItem) -> Unit,
    onFilterByGroup: (Int) -> Unit,
    modifier: Modifier = Modifier,
    drag: CalendarDragState,
) {
    val tasks = state.sidebarItems
    val date = state.selectedDate
    val recurring = state.sidebarRecurringItems
    val events = state.sidebarEventItems
    val mainTasks = state.sidebarMainTaskItems
    val completed = state.sidebarCompletedItems
    val isDayCompleted = state.isSelectedDayCompleted
    val entry: @Composable (CalendarItem) -> Unit = { item ->
        CalendarDayEntry(
            item = item,
            state = state,
            drag = drag,
            onIntent = onIntent,
            onOpenTask = onOpenTask,
            onFilterByGroup = onFilterByGroup,
        )
    }
    val sectionGap = 16.dp
    var recurringExpanded by remember(key1 = date) { mutableStateOf(value = true) }
    var eventsExpanded by remember(key1 = date) { mutableStateOf(value = true) }
    var tasksExpanded by remember(key1 = date) { mutableStateOf(value = true) }
    var completedExpanded by remember(key1 = date) { mutableStateOf(value = true) }
    val colors = TaskBTheme.colors
    Column(modifier = modifier) {
        if (tasks.isEmpty())
            Text(
                text = "No tasks or events for this day.",
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
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                if (isDayCompleted)
                    item(key = "day-completed") {
                        CalendarDayCompleted(
                            modifier = Modifier
                                .padding(paddingValues = calendarSidebarTaskPadding)
                                .padding(bottom = 24.dp)
                        )
                    }
                if (recurring.isNotEmpty())
                    item(key = "recurring-heading") {
                        CalendarSidebarSectionHeader(
                            title = "Recurring · ${recurring.size}",
                            expanded = recurringExpanded,
                            onToggle = { recurringExpanded = !recurringExpanded },
                            modifier = Modifier
                                .padding(paddingValues = calendarSidebarTaskPadding)
                                .testTag(tag = "calendar-sidebar-recurring-heading"),
                        )
                        if (recurringExpanded) Spacer(modifier = Modifier.height(height = 8.dp))
                    }
                if (recurringExpanded)
                    calendarDayEntries(entries = recurring, entry = entry)
                if (events.isNotEmpty())
                    item(key = "events-heading") {
                        if (recurring.isNotEmpty()) Spacer(modifier = Modifier.height(height = sectionGap))
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
                    calendarDayEntries(entries = events, entry = entry, eventSpacing = true)
                if (mainTasks.isNotEmpty())
                    item(key = "tasks-heading") {
                        if (events.isNotEmpty()) {
                            val gap = if (eventsExpanded) sectionGap - 4.dp else sectionGap
                            Spacer(modifier = Modifier.height(height = gap))
                        } else if (recurring.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(height = sectionGap))
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
                if (tasksExpanded)
                    calendarDayEntries(entries = mainTasks, entry = entry)
                if (completed.isNotEmpty())
                    item(key = "completed-heading") {
                        if (mainTasks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(height = sectionGap))
                        } else if (events.isNotEmpty()) {
                            val gap = if (eventsExpanded) sectionGap - 4.dp else sectionGap
                            Spacer(modifier = Modifier.height(height = gap))
                        } else if (recurring.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(height = sectionGap))
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
                    calendarDayEntries(entries = completed, entry = entry)
            }
            if (scroll.canScrollForward || scroll.canScrollBackward)
                TaskBVerticalScrollbar(state = scroll, modifier = Modifier.fillMaxHeight())
        }
    }
}

private fun LazyListScope.calendarDayEntries(
    entries: List<CalendarItem>,
    entry: @Composable (CalendarItem) -> Unit,
    eventSpacing: Boolean = false,
) {
    items(items = entries, key = CalendarItem::key) { item ->
        entry(item)
        if (eventSpacing) {
            Spacer(modifier = Modifier.height(height = 4.dp))
        }
    }
}

@Composable
internal fun CalendarDayEntry(
    item: CalendarItem,
    state: CalendarState,
    drag: CalendarDragState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenTask: (CalendarItem) -> Unit,
    onFilterByGroup: (Int) -> Unit,
) {
    val date = state.selectedDate ?: item.dueDate
    val quickDelete = (item as? CalendarItem.TaskItem)?.task?.quickDeleteIntent()
    val enabled = !item.isHoliday &&
        (item.isEvent || item.id !in state.pendingTaskUpdates) &&
        state.pendingMove == null
    val tag = if (item is CalendarItem.TaskItem) "calendar-task-${item.key}" else "calendar-${item.key}"
    CalendarTaskRow(
        task = item,
        group = state.groups.find { group -> group.id == item.groupId },
        onOpenTask = { onOpenTask(item) },
        onFilterByGroup = onFilterByGroup,
        onFilterByDate = { selected -> onIntent(CalendarIntent.FilterByDate(date = selected)) },
        onToggle = { taskId ->
            onIntent(CalendarIntent.ToggleCompleted(id = taskId, occurrenceId = item.occurrenceId))
        },
        modifier = Modifier
            .testTag(tag = tag)
            .calendarTaskDrag(task = item, dragState = drag, enabled = enabled, sourceDate = date) { _, target ->
                onIntent(item.moveTo(date = target))
            },
        enabled = enabled,
        dragging = drag.item?.key == item.key,
        date = date,
        now = state.now,
        today = state.today,
        contentPadding = calendarSidebarTaskPadding,
        onQuickDelete = quickDelete?.let { intent -> { onIntent(intent) } },
    )
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
