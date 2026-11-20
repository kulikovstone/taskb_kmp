package com.mkulikov.taskb.feature.calendar.screen.calendar_two

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.component.CalendarDayContext
import com.mkulikov.taskb.feature.calendar.component.CalendarDayList
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarDragState
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarIntent
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarItem
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarState
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarSidebarDateLabel
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarTaskDrag
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskFilter
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding

@Composable
internal fun Calendar2Sidebar(
    state: CalendarState,
    query: TextFieldState,
    filter: TaskFilter?,
    groupId: Int?,
    group: Group?,
    drag: CalendarDragState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenItem: (CalendarItem) -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilter: () -> Unit,
    onClearGroup: () -> Unit,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TaskBTheme.colors
    val date = state.selectedDate
    val dayAgenda = date != null && query.text.isBlank() && filter == null && groupId == null
    val needsCalendar = date != null
    val isLoading = if (needsCalendar) state.isLoading else state.taskList == null
    val error = if (needsCalendar) state.loadError else state.taskListLoadError
    Column(modifier = modifier.testTag(tag = "calendar-2-sidebar")) {
        Divider(color = colors.border)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 23.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                TaskBTextField(
                    state = query,
                    description = "Search tasks",
                    placeholder = "Search tasks",
                    search = true,
                    modifier = Modifier.weight(weight = 1f),
                )
                IconButton(onClick = onOpenFilters, modifier = Modifier.size(size = 44.dp)) {
                    Icon(imageVector = TaskBIcons.Filter, contentDescription = "Filters", tint = colors.accent)
                }
                Box(
                    modifier = Modifier
                        .size(size = 44.dp)
                        .clip(shape = RoundedCornerShape(size = 10.dp))
                        .background(color = colors.actionFill)
                        .clickable(role = Role.Button, onClick = onCreateTask),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = TaskBIcons.Add, contentDescription = "Add task", tint = colors.onAction)
                }
            }
            if (date != null) {
                ActiveFilterChip(
                    label = calendarSidebarDateLabel.format(value = date),
                    icon = TaskBIcons.Calendar,
                    iconTint = colors.accent,
                    clearDescription = "Clear date filter",
                    onClear = { onIntent(CalendarIntent.ShowUnscheduled) },
                )
            }
            if (filter != null) {
                ActiveFilterChip(
                    label = filter.label,
                    icon = TaskBIcons.Filter,
                    iconTint = colors.accent,
                    clearDescription = "Clear task filter",
                    onClear = onClearFilter,
                )
            }
            if (groupId != null) {
                ActiveFilterChip(
                    label = group?.name ?: "No group",
                    icon = TaskBIcons.FolderFilled,
                    iconTint = group?.color?.color ?: colors.muted,
                    clearDescription = "Clear group filter",
                    onClear = onClearGroup,
                )
            }
        }
        when {
            error != null -> TaskBLoadError(
                message = error,
                onRetry = { onIntent(if (needsCalendar) CalendarIntent.RetryLoad else CalendarIntent.RetryTaskList) },
            )
            isLoading -> TaskBLoading(modifier = Modifier.weight(weight = 1f))
            dayAgenda -> {
                CalendarDayContext(
                    state = state,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 12.dp),
                )
                CalendarDayList(
                    state = state,
                    drag = drag,
                    onIntent = onIntent,
                    onOpenTask = onOpenItem,
                    modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
                )
            }
            else -> {
                val tasks = state.filteredTasks(query = query.text.toString(), filter = filter, groupId = groupId)
                Text(
                    text = "${tasks.size} ${if (tasks.size == 1) "task" else "tasks"}",
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
                    color = colors.muted,
                    style = TaskBTheme.typography.small,
                )
                if (tasks.isEmpty()) {
                    Text(
                        text = "No tasks to show.",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = colors.muted,
                        style = TaskBTheme.typography.body,
                    )
                } else {
                    val scroll = rememberLazyListState()
                    val groupsById = state.groups.associateBy(keySelector = Group::id)
                    Box(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                        LazyColumn(
                            state = scroll,
                            modifier = Modifier.fillMaxSize().testTag(tag = "calendar-2-task-list"),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            items(items = tasks, key = Task::key) { task ->
                                val item = CalendarItem.TaskItem(task = task)
                                Calendar2TaskRow(
                                    item = item,
                                    group = groupsById[task.groupId],
                                    today = state.today,
                                    enabled = task.id !in state.pendingCompletion && state.pendingMove == null,
                                    drag = drag,
                                    onIntent = onIntent,
                                    onOpen = { onOpenItem(item) },
                                )
                            }
                        }
                        if (scroll.canScrollForward || scroll.canScrollBackward) {
                            TaskBVerticalScrollbar(
                                state = scroll,
                                modifier = Modifier.align(alignment = Alignment.CenterEnd).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    clearDescription: String,
    onClear: () -> Unit,
) {
    val colors = TaskBTheme.colors
    Row(
        modifier = Modifier
            .height(height = 32.dp)
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = colors.selected)
            .clickable(role = Role.Button, onClickLabel = clearDescription, onClick = onClear)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size = 20.dp),
        )
        Text(
            text = label,
            color = colors.accent,
            style = TaskBTheme.typography.button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f, fill = false),
        )
        Icon(
            imageVector = TaskBIcons.Clear,
            contentDescription = clearDescription,
            tint = colors.accent,
            modifier = Modifier.size(size = 16.dp),
        )
    }
}

@Composable
private fun Calendar2TaskRow(
    item: CalendarItem.TaskItem,
    group: Group?,
    today: LocalDate,
    enabled: Boolean,
    drag: CalendarDragState,
    onIntent: (CalendarIntent) -> Unit,
    onOpen: () -> Unit,
) {
    val task = item.task
    val colors = TaskBTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .testTag(tag = "calendar-2-task-${task.key}")
            .graphicsLayer { alpha = if (drag.item?.key == item.key) 0.25f else 1f }
            .calendarTaskDrag(task = item, dragState = drag, enabled = enabled) { _, target ->
                onIntent(item.moveTo(date = target))
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
            .padding(start = 12.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size = 44.dp)
                .toggleable(
                    value = task.isCompleted,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = {
                        onIntent(CalendarIntent.ToggleCompleted(id = task.id, occurrenceId = task.occurrenceId))
                    },
                )
                .semantics {
                    contentDescription = if (task.isCompleted) {
                        "Mark ${task.name} incomplete"
                    } else {
                        "Complete ${task.name}"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (task.isCompleted) TaskBIcons.Checked else TaskBIcons.Unchecked,
                contentDescription = null,
                tint = if (task.isCompleted) colors.accent else colors.muted,
                modifier = Modifier.size(size = 24.dp),
            )
        }
        Column(
            modifier = Modifier.weight(weight = 1f),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            Text(
                text = task.name,
                color = if (task.isCompleted) colors.completedText else colors.text,
                style = TaskBTheme.typography.body,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    color = colors.muted,
                    style = TaskBTheme.typography.small,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Row(
                    modifier = Modifier.weight(weight = 1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
                ) {
                    if (group != null) {
                        Icon(
                            imageVector = TaskBIcons.FolderFilled,
                            contentDescription = null,
                            tint = group.color.color,
                            modifier = Modifier.size(size = 14.dp),
                        )
                        Text(
                            text = group.name,
                            color = colors.muted,
                            style = TaskBTheme.typography.small,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!task.isCompleted) {
                    task.dueDate?.let { date ->
                        Text(
                            text = if (date.year == today.year) taskDateLabel.format(value = date) else {
                                calendarSidebarDateLabel.format(value = date)
                            },
                            color = if (date < today) colors.danger else colors.muted,
                            style = TaskBTheme.typography.small,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private val taskDateLabel = LocalDate.Format {
    dayOfWeek(names = DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(value = ", ")
    monthName(names = MonthNames.ENGLISH_ABBREVIATED)
    chars(value = " ")
    day(padding = Padding.NONE)
}
