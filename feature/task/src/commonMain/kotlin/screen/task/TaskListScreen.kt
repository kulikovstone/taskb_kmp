package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import com.mkulikov.taskb.feature.task.api.model.Task

@Composable
internal fun TaskListScreen(
    state: TaskState, onIntent: (TaskIntent) -> Unit,
    wide: Boolean, onBack: () -> Unit, onChooseGroup: () -> Unit, onNewTask: () -> Unit, onOpenTask: (Int) -> Unit,
    onOpenOccurrence: ((Task) -> Unit)? = null,
) {
    val actions: @Composable RowScope.() -> Unit = {
        IconButton(onChooseGroup) {
            Icon(TaskBIcons.Folder, "Choose group", tint = TaskBTheme.colors.accent)
        }
        IconButton({ onIntent(TaskIntent.Choose(TaskSelection.Filters)) }) {
            Icon(TaskBIcons.Filter, "Choose task filter", tint = TaskBTheme.colors.accent)
        }
        if (wide) {
            Spacer(Modifier.width(16.dp))
            TaskBEditorButton("New task", TaskBIcons.Add, onNewTask)
        } else IconButton(onNewTask) { Icon(TaskBIcons.Add, "Add task", tint = TaskBTheme.colors.accent) }
    }
    Column(Modifier.fillMaxSize()) {
        if (wide) Row(Modifier.fillMaxWidth().height(96.dp).testTag("task-toolbar").padding(start = 16.dp, end = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(TaskBIcons.Back, "Back", tint = TaskBTheme.colors.heading) }
            Spacer(Modifier.width(8.dp))
            Text("Tasks", modifier = Modifier.offset(y = (-2).dp),
                style = TaskBTheme.typography.display, color = TaskBTheme.colors.heading)
            Spacer(Modifier.width(16.dp))
            TaskSummary(state, wide)
            Spacer(Modifier.weight(1f))
            actions()
        } else TaskBTopAppBar("Tasks", onBack, modifier = Modifier.testTag("task-toolbar"), titleSpacing = 4.dp,
            subtitle = { TaskSummary(state, wide) }, actions = actions)
        Column(Modifier.weight(1f).padding(top = if (wide) 8.dp else 12.dp)) {
            if (wide) Row(Modifier.fillMaxWidth().padding(horizontal = taskListHorizontalPadding(wide)),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TaskBTextField(state.query, "Search tasks", placeholder = "Search tasks", search = true, modifier = Modifier.width(300.dp))
                ActiveTaskFilters(state, onIntent, onChooseGroup, Modifier.weight(1f))
            } else {
                Column(Modifier.padding(horizontal = taskListHorizontalPadding(wide))) {
                    TaskBTextField(state.query, "Search tasks", placeholder = "Search tasks", search = true, modifier = Modifier.fillMaxWidth())
                    if (state.filter != null || state.groupFilter != TaskGroupFilter.All) {
                        Spacer(Modifier.height(8.dp))
                        ActiveTaskFilters(state, onIntent, onChooseGroup)
                    }
                }
            }
            Spacer(Modifier.height(if (wide) 12.dp else 8.dp))
            TaskDirectory(state, onIntent, wide, onOpenTask, Modifier.weight(1f).fillMaxWidth(), onOpenOccurrence)
        }
    }
}

@Composable
private fun TaskSummary(state: TaskState, wide: Boolean) {
    Text(state.summary, modifier = Modifier.testTag("task-summary"), color = TaskBTheme.colors.muted,
        style = if (wide) TaskBTheme.typography.body else TaskBTheme.typography.small,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ActiveTaskFilters(state: TaskState, onIntent: (TaskIntent) -> Unit, onChooseGroup: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.groupFilter != TaskGroupFilter.All) {
            val group = state.groups.find { it.id == (state.groupFilter as? TaskGroupFilter.Selected)?.id }
            val groupIcon = if (group == null) TaskBIcons.Folder else TaskBIcons.FolderFilled
            TaskFilterChip(group?.name ?: "No group", groupIcon, group?.color?.color ?: TaskBTheme.colors.accent,
                onChooseGroup, { onIntent(TaskIntent.FilterGroup(TaskGroupFilter.All)) }, "Clear group filter")
        }
        state.filter?.let { TaskFilterChip(it.label, TaskBIcons.Filter, TaskBTheme.colors.accent,
            { onIntent(TaskIntent.Choose(TaskSelection.Filters)) }, { onIntent(TaskIntent.Filter(null)) }, "Clear task filter") }
    }
}

@Composable
private fun TaskFilterChip(label: String, icon: ImageVector, tint: Color, onChange: () -> Unit, onClear: () -> Unit, clearDescription: String) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.height(44.dp).clip(shape).background(TaskBTheme.colors.selected), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.height(44.dp).clickable(onClick = onChange).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(label, color = TaskBTheme.colors.accent, style = TaskBTheme.typography.button)
        }
        Box(
            Modifier.size(44.dp).clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TaskBIcons.Clear, clearDescription, tint = TaskBTheme.colors.accent, modifier = Modifier.size(24.dp))
        }
    }
}
