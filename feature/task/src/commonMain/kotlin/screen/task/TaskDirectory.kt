package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
internal fun TaskDirectory(state: TaskState, onIntent: (TaskIntent) -> Unit, wide: Boolean, onOpenTask: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = TaskBTheme.colors
    Column(modifier) {
        Text(state.summary, color = colors.muted, style = TaskBTheme.typography.body, modifier = Modifier.fillMaxWidth().height(28.dp).wrapContentHeight(Alignment.CenterVertically))
        Spacer(Modifier.height(if (wide) 16.dp else 8.dp))
        if (wide) {
            Row(Modifier.fillMaxWidth().height(49.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.width(44.dp))
                TaskColumnHeading("Name", TaskSort.Name, state, onIntent, Modifier.weight(1f))
                TaskColumnHeading("Group", TaskSort.Group, state, onIntent, Modifier.width(164.dp))
                TaskColumnHeading("Date", TaskSort.Date, state, onIntent, Modifier.width(112.dp), true)
            }
            Divider(color = colors.border)
        }
        when {
            state.isLoading -> TaskBLoading(Modifier.weight(1f))
            state.loadError != null -> TaskBLoadError(state.loadError, { onIntent(TaskIntent.RetryLoad) })
            state.visibleTasks.isEmpty() -> Text(if (state.tasks.isEmpty()) "No tasks yet." else "No tasks match your search or filters.",
                color = colors.muted, style = TaskBTheme.typography.body, modifier = Modifier.padding(vertical = 24.dp))
            else -> {
                val scroll = rememberLazyListState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize()) {
                        items(state.visibleTasks, key = { it.id }) { task ->
                            TaskRow(task, state.groups.find { it.id == task.groupId }, state, wide, onIntent, onOpenTask)
                        }
                    }
                    TaskBVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun TaskColumnHeading(label: String, sort: TaskSort, state: TaskState, onIntent: (TaskIntent) -> Unit, modifier: Modifier, end: Boolean = false) {
    TaskBSortHeader(
        label = label,
        isActive = state.sort == sort,
        reverseSort = state.reverseSort,
        onClick = { onIntent(TaskIntent.Sort(sort)) },
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = if (end) Arrangement.End else Arrangement.Start,
    )
}

@Composable
private fun TaskRow(task: Task, group: Group?, state: TaskState, wide: Boolean, onIntent: (TaskIntent) -> Unit, onOpenTask: (Int) -> Unit) {
    val colors = TaskBTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onOpenTask(task.id) })
            .then(if (wide) Modifier.height(63.dp).padding(horizontal = 20.dp) else Modifier.padding(vertical = 16.dp)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).toggleable(task.isCompleted, enabled = task.id !in state.pendingCompletion, role = Role.Checkbox,
                onValueChange = { onIntent(TaskIntent.ToggleCompleted(task.id)) })
                .semantics { contentDescription = if (task.isCompleted) "Mark ${task.name} incomplete" else "Complete ${task.name}" }, contentAlignment = Alignment.Center) {
                Icon(if (task.isCompleted) TaskBIcons.Checked else TaskBIcons.Unchecked, null, tint = if (task.isCompleted) colors.accent else colors.muted, modifier = Modifier.size(24.dp))
            }
            val title: @Composable () -> Unit = {
                Text(task.name, color = if (task.isCompleted) colors.completedText else colors.text, style = TaskBTheme.typography.body,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null, maxLines = if (wide) 1 else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 22.dp))
            }
            if (wide) {
                Box(Modifier.weight(1f)) { title() }
                TaskGroupLabel(group, Modifier.width(164.dp), false)
                Box(Modifier.width(112.dp), contentAlignment = Alignment.CenterEnd) { TaskDueDate(task, state, true) }
            } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                title()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskGroupLabel(group, Modifier.weight(1f), true)
                    TaskDueDate(task, state, false)
                }
            }
        }
        Divider(color = colors.border)
    }
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
