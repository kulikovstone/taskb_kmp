package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.component.taskDateLabel
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBSortHeader
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

internal fun taskListHorizontalPadding(wide: Boolean) = if (wide) 32.dp else 24.dp

private val taskRowGap = 12.dp
private val taskCheckboxSize = 44.dp

@Composable
internal fun TaskDirectory(
    state: TaskState,
    onIntent: (TaskIntent) -> Unit,
    wide: Boolean,
    onOpenTask: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenOccurrence: ((Task) -> Unit)? = null,
) {
    val colors = TaskBTheme.colors
    Column(modifier = modifier) {
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().height(height = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskColumnHeading(
                    label = "Name",
                    sort = TaskSort.Name,
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier
                        .weight(weight = 0.5f)
                        .padding(start = taskCheckboxSize + taskRowGap * 2, end = taskRowGap),
                )
                TaskColumnHeading(
                    label = "Group",
                    sort = TaskSort.Group,
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(weight = 0.2f).padding(horizontal = taskRowGap),
                )
                TaskColumnHeading(
                    label = "Date",
                    sort = TaskSort.Date,
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(weight = 0.15f).padding(horizontal = taskRowGap),
                )
                Text(
                    text = "Repeat",
                    modifier = Modifier.weight(weight = 0.15f).padding(horizontal = taskRowGap),
                    color = colors.secondary,
                    style = TaskBTheme.typography.label,
                )
            }
            Divider(modifier = Modifier.fillMaxWidth().testTag(tag = "task-columns-divider"), color = colors.border)
        }
        when {
            state.isLoading -> TaskBLoading(modifier = Modifier.weight(weight = 1f))
            state.loadError != null -> TaskBLoadError(
                message = state.loadError,
                onRetry = { onIntent(TaskIntent.RetryLoad) },
            )
            state.visibleTasks.isEmpty() -> Text(
                text = if (state.taskCount == 0) "No tasks yet." else "No tasks match your search or filters.",
                color = colors.muted,
                style = TaskBTheme.typography.body,
                modifier = Modifier
                    .padding(horizontal = taskListHorizontalPadding(wide = wide), vertical = 24.dp),
            )
            else -> {
                val scroll = rememberLazyListState()
                Box(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag(tag = "task-list")) {
                        items(items = state.visibleTasks, key = Task::key) { task ->
                            TaskRow(
                                task = task,
                                group = state.groups.find { group -> group.id == task.groupId },
                                state = state,
                                wide = wide,
                                onIntent = onIntent,
                                onOpenTask = { taskId -> onOpenOccurrence?.invoke(task) ?: onOpenTask(taskId) },
                            )
                        }
                    }
                    TaskBVerticalScrollbar(
                        state = scroll,
                        modifier = Modifier.align(alignment = Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskColumnHeading(
    label: String,
    sort: TaskSort,
    state: TaskState,
    onIntent: (TaskIntent) -> Unit,
    modifier: Modifier,
) {
    TaskBSortHeader(
        label = label,
        isActive = state.sort == sort,
        reverseSort = state.reverseSort,
        onClick = { onIntent(TaskIntent.Sort(sort = sort)) },
        modifier = modifier.fillMaxHeight(),
    )
}

@Composable
private fun TaskRow(
    task: Task,
    group: Group?,
    state: TaskState,
    wide: Boolean,
    onIntent: (TaskIntent) -> Unit,
    onOpenTask: (Int) -> Unit,
) {
    val colors = TaskBTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val verticalPadding = if (wide) 4.dp else 8.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.surface)
            .testTag(tag = "task-row-${task.key}")
            .indication(interactionSource = interactionSource, indication = LocalIndication.current)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onOpenTask(task.id) },
            )
            .padding(
                start = if (wide) 0.dp else taskRowGap,
                end = if (wide) 0.dp else taskListHorizontalPadding(wide = wide),
                top = verticalPadding,
                bottom = verticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = if (wide) {
                Modifier.weight(weight = 0.5f).padding(horizontal = taskRowGap)
            } else {
                Modifier.fillMaxWidth()
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = taskRowGap),
        ) {
            Box(
                modifier = Modifier
                    .size(size = taskCheckboxSize)
                    .toggleable(
                        value = task.isCompleted,
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = task.id !in state.pendingCompletion,
                        role = Role.Checkbox,
                        onValueChange = {
                            onIntent(TaskIntent.ToggleCompleted(id = task.id, occurrenceId = task.occurrenceId))
                        },
                    )
                    .semantics {
                        contentDescription =
                            if (task.isCompleted) "Mark ${task.name} incomplete" else "Complete ${task.name}"
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
            if (wide) {
                TaskTitle(task = task, wide = wide, modifier = Modifier.weight(weight = 1f))
            } else {
                Column(
                    modifier = Modifier.weight(weight = 1f),
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp),
                ) {
                    TaskTitle(task = task, wide = wide)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                    ) {
                        TaskGroupLabel(group = group, modifier = Modifier.weight(weight = 1f), small = true)
                        TaskDueDate(task = task, state = state, wide = false)
                    }
                }
            }
        }
        if (wide) {
            TaskGroupLabel(
                group = group,
                modifier = Modifier.weight(weight = 0.2f).padding(horizontal = taskRowGap),
                small = false,
            )
            Box(
                modifier = Modifier.weight(weight = 0.15f).padding(horizontal = taskRowGap),
                contentAlignment = Alignment.CenterStart,
            ) {
                TaskDueDate(task = task, state = state, wide = true)
            }
            TaskRepeatLabel(
                task = task,
                modifier = Modifier.weight(weight = 0.15f).padding(horizontal = taskRowGap),
            )
        }
    }
}

@Composable
private fun TaskTitle(task: Task, wide: Boolean, modifier: Modifier = Modifier) {
    val colors = TaskBTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        Text(
            text = task.name,
            color = if (task.isCompleted) colors.completedText else colors.text,
            style = TaskBTheme.typography.body,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            maxLines = if (wide) 1 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 22.dp),
        )
        if (task.description.isNotBlank())
            Text(
                text = task.description,
                color = colors.muted,
                style = TaskBTheme.typography.small,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
    }
}

@Composable
private fun TaskRepeatLabel(task: Task, modifier: Modifier) {
    Text(
        text = task.recurrence?.frequency?.name.orEmpty(),
        modifier = modifier.testTag(tag = "task-repeat-${task.key}"),
        color = TaskBTheme.colors.muted,
        style = TaskBTheme.typography.body,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TaskGroupLabel(group: Group?, modifier: Modifier, small: Boolean) {
    Row(
        modifier = modifier.heightIn(min = if (small) 18.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = if (small) 4.dp else 8.dp),
    ) {
        if (group != null) {
            Icon(
                imageVector = TaskBIcons.FolderFilled,
                contentDescription = null,
                tint = group.color.color,
                modifier = Modifier.size(size = if (small) 14.dp else 18.dp),
            )
            Text(
                text = group.name,
                style = if (small) TaskBTheme.typography.small else TaskBTheme.typography.body,
                color = if (small) TaskBTheme.colors.muted else TaskBTheme.colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TaskDueDate(task: Task, state: TaskState, wide: Boolean) {
    if (!task.isCompleted)
        task.dueDate?.let { date ->
            Text(
                text = taskDateLabel(date = date, today = state.today),
                style = if (wide) TaskBTheme.typography.body else TaskBTheme.typography.small,
                color = if (date < state.today) TaskBTheme.colors.danger else TaskBTheme.colors.muted,
                maxLines = 1,
            )
        }
}
