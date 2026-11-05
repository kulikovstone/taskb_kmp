package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.feature.task.component.taskDateLabel
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.component.TaskBSortHeader
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

internal fun taskListHorizontalPadding(wide: Boolean) = if (wide) 32.dp else 24.dp

private val taskRowGap = 12.dp
private val taskCheckboxSize = 44.dp

@Composable
internal fun TaskDirectory(state: TaskState, onIntent: (TaskIntent) -> Unit, wide: Boolean, onOpenTask: (Int) -> Unit, modifier: Modifier = Modifier, onOpenOccurrence: ((Task) -> Unit)? = null) {
    val colors = TaskBTheme.colors
    Column(modifier) {
        if (wide) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskColumnHeading("Name", TaskSort.Name, state, onIntent,
                    Modifier.weight(0.5f).padding(start = taskCheckboxSize + taskRowGap * 2, end = taskRowGap))
                TaskColumnHeading("Group", TaskSort.Group, state, onIntent, Modifier.weight(0.2f).padding(horizontal = taskRowGap))
                TaskColumnHeading("Date", TaskSort.Date, state, onIntent, Modifier.weight(0.15f).padding(horizontal = taskRowGap))
                Text("Repeat", modifier = Modifier.weight(0.15f).padding(horizontal = taskRowGap), color = colors.secondary, style = TaskBTheme.typography.label)
            }
            Divider(Modifier.fillMaxWidth().testTag("task-columns-divider"), color = colors.border)
        }
        when {
            state.isLoading -> TaskBLoading(Modifier.weight(1f))
            state.loadError != null -> TaskBLoadError(state.loadError, { onIntent(TaskIntent.RetryLoad) })
            state.visibleTasks.isEmpty() -> Text(if (state.taskCount == 0) "No tasks yet." else "No tasks match your search or filters.",
                color = colors.muted, style = TaskBTheme.typography.body,
                modifier = Modifier.padding(horizontal = taskListHorizontalPadding(wide), vertical = 24.dp))
            else -> {
                val scroll = rememberLazyListState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("task-list")) {
                        items(state.visibleTasks, key = { it.key }) { task ->
                            TaskRow(task, state.groups.find { it.id == task.groupId }, state, wide, onIntent, { onOpenOccurrence?.invoke(task) ?: onOpenTask(it) })
                        }
                    }
                    TaskBVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun TaskColumnHeading(label: String, sort: TaskSort, state: TaskState, onIntent: (TaskIntent) -> Unit, modifier: Modifier) {
    TaskBSortHeader(
        label = label,
        isActive = state.sort == sort,
        reverseSort = state.reverseSort,
        onClick = { onIntent(TaskIntent.Sort(sort)) },
        modifier = modifier.fillMaxHeight(),
    )
}

@Composable
private fun TaskRow(task: Task, group: Group?, state: TaskState, wide: Boolean, onIntent: (TaskIntent) -> Unit, onOpenTask: (Int) -> Unit) {
    val colors = TaskBTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val verticalPadding = if (wide) 4.dp else 8.dp
    Row(
        Modifier.fillMaxWidth().background(colors.surface).testTag("task-row-${task.key}")
            .indication(interactionSource, LocalIndication.current)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = { onOpenTask(task.id) })
            .padding(start = if (wide) 0.dp else taskRowGap, end = if (wide) 0.dp else taskListHorizontalPadding(wide),
                top = verticalPadding, bottom = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(if (wide) Modifier.weight(0.5f).padding(horizontal = taskRowGap) else Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(taskRowGap)) {
            Box(Modifier.size(taskCheckboxSize).toggleable(task.isCompleted, interactionSource = interactionSource, indication = null,
                enabled = task.id !in state.pendingCompletion, role = Role.Checkbox,
                onValueChange = { onIntent(TaskIntent.ToggleCompleted(task.id, task.occurrenceId)) })
                .semantics { contentDescription = if (task.isCompleted) "Mark ${task.name} incomplete" else "Complete ${task.name}" }, contentAlignment = Alignment.Center) {
                Icon(if (task.isCompleted) TaskBIcons.Checked else TaskBIcons.Unchecked, null, tint = if (task.isCompleted) colors.accent else colors.muted, modifier = Modifier.size(24.dp))
            }
            if (wide) TaskTitle(task, wide, Modifier.weight(1f))
            else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TaskTitle(task, wide)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskGroupLabel(group, Modifier.weight(1f), true)
                    TaskDueDate(task, state, false)
                }
            }
        }
        if (wide) {
            TaskGroupLabel(group, Modifier.weight(0.2f).padding(horizontal = taskRowGap), false)
            Box(Modifier.weight(0.15f).padding(horizontal = taskRowGap), contentAlignment = Alignment.CenterStart) { TaskDueDate(task, state, true) }
            TaskRepeatLabel(task, Modifier.weight(0.15f).padding(horizontal = taskRowGap))
        }
    }
}

@Composable
private fun TaskTitle(task: Task, wide: Boolean, modifier: Modifier = Modifier) {
    val colors = TaskBTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(task.name, color = if (task.isCompleted) colors.completedText else colors.text, style = TaskBTheme.typography.body,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null, maxLines = if (wide) 1 else 3,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 22.dp))
        if (task.description.isNotBlank()) Text(task.description, color = colors.muted, style = TaskBTheme.typography.small,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TaskRepeatLabel(task: Task, modifier: Modifier) {
    Text(task.recurrence?.frequency?.name.orEmpty(), modifier = modifier.testTag("task-repeat-${task.key}"),
        color = TaskBTheme.colors.muted, style = TaskBTheme.typography.body,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun TaskGroupLabel(group: Group?, modifier: Modifier, small: Boolean) {
    Row(modifier.heightIn(min = if (small) 18.dp else 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp)) {
        if (group != null) {
            Icon(TaskBIcons.FolderFilled, null, tint = group.color.color, modifier = Modifier.size(if (small) 14.dp else 18.dp))
            Text(group.name, style = if (small) TaskBTheme.typography.small else TaskBTheme.typography.body,
                color = if (small) TaskBTheme.colors.muted else TaskBTheme.colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TaskDueDate(task: Task, state: TaskState, wide: Boolean) {
    if (!task.isCompleted) task.dueDate?.let { date ->
        Text(taskDateLabel(date, state.today), style = if (wide) TaskBTheme.typography.body else TaskBTheme.typography.small,
            color = if (date < state.today) TaskBTheme.colors.danger else TaskBTheme.colors.muted, maxLines = 1)
    }
}
