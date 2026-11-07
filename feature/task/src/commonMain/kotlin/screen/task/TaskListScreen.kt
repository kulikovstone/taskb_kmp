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
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun TaskListScreen(
    state: TaskState,
    onIntent: (TaskIntent) -> Unit,
    wide: Boolean,
    onBack: () -> Unit,
    onChooseGroup: () -> Unit,
    onNewTask: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onOpenOccurrence: ((Task) -> Unit)? = null,
) {
    val actions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = onChooseGroup) {
            Icon(
                imageVector = TaskBIcons.Folder,
                contentDescription = "Choose group",
                tint = TaskBTheme.colors.accent,
            )
        }
        IconButton(onClick = { onIntent(TaskIntent.Choose(selection = TaskSelection.Filters)) }) {
            Icon(
                imageVector = TaskBIcons.Filter,
                contentDescription = "Choose task filter",
                tint = TaskBTheme.colors.accent,
            )
        }
        if (wide) {
            Spacer(modifier = Modifier.width(width = 16.dp))
            TaskBEditorButton(label = "New task", icon = TaskBIcons.Add, onClick = onNewTask)
        } else {
            IconButton(onClick = onNewTask) {
                Icon(imageVector = TaskBIcons.Add, contentDescription = "Add task", tint = TaskBTheme.colors.accent)
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height = 96.dp)
                    .testTag(tag = "task-toolbar")
                    .padding(start = 16.dp, end = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = TaskBIcons.Back,
                        contentDescription = "Back",
                        tint = TaskBTheme.colors.heading,
                    )
                }
                Spacer(modifier = Modifier.width(width = 8.dp))
                Text(
                    text = "Tasks",
                    modifier = Modifier.offset(y = (-2).dp),
                    style = TaskBTheme.typography.display,
                    color = TaskBTheme.colors.heading,
                )
                Spacer(modifier = Modifier.width(width = 16.dp))
                TaskSummary(state = state, wide = wide)
                Spacer(modifier = Modifier.weight(weight = 1f))
                actions()
            }
        } else {
            TaskBTopAppBar(
                title = "Tasks",
                onBack = onBack,
                modifier = Modifier.testTag(tag = "task-toolbar"),
                titleSpacing = 4.dp,
                subtitle = { TaskSummary(state = state, wide = wide) },
                actions = actions,
            )
        }
        Column(modifier = Modifier.weight(weight = 1f).padding(top = if (wide) 8.dp else 12.dp)) {
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = taskListHorizontalPadding(wide = wide)),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(space = 24.dp),
                ) {
                    TaskBTextField(
                        state = state.query,
                        description = "Search tasks",
                        placeholder = "Search tasks",
                        search = true,
                        modifier = Modifier.width(width = 300.dp),
                    )
                    ActiveTaskFilters(
                        state = state,
                        onIntent = onIntent,
                        onChooseGroup = onChooseGroup,
                        modifier = Modifier.weight(weight = 1f),
                    )
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = taskListHorizontalPadding(wide = wide))) {
                    TaskBTextField(
                        state = state.query,
                        description = "Search tasks",
                        placeholder = "Search tasks",
                        search = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.filter != null || state.groupFilter != TaskGroupFilter.All) {
                        Spacer(modifier = Modifier.height(height = 8.dp))
                        ActiveTaskFilters(state = state, onIntent = onIntent, onChooseGroup = onChooseGroup)
                    }
                }
            }
            Spacer(modifier = Modifier.height(height = if (wide) 12.dp else 8.dp))
            TaskDirectory(
                state = state,
                onIntent = onIntent,
                wide = wide,
                onOpenTask = onOpenTask,
                modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
                onOpenOccurrence = onOpenOccurrence,
            )
        }
    }
}

@Composable
private fun TaskSummary(state: TaskState, wide: Boolean) {
    Text(
        text = state.summary,
        modifier = Modifier.testTag(tag = "task-summary"),
        color = TaskBTheme.colors.muted,
        style = if (wide) TaskBTheme.typography.body else TaskBTheme.typography.small,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ActiveTaskFilters(
    state: TaskState,
    onIntent: (TaskIntent) -> Unit,
    onChooseGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        if (state.groupFilter != TaskGroupFilter.All) {
            val group = state.groups.find { group -> group.id == (state.groupFilter as? TaskGroupFilter.Selected)?.id }
            val groupIcon = if (group == null) TaskBIcons.Folder else TaskBIcons.FolderFilled
            TaskFilterChip(
                label = group?.name ?: "No group",
                icon = groupIcon,
                tint = group?.color?.color ?: TaskBTheme.colors.accent,
                onChange = onChooseGroup,
                onClear = { onIntent(TaskIntent.FilterGroup(filter = TaskGroupFilter.All)) },
                clearDescription = "Clear group filter",
            )
        }
        state.filter?.let { filter ->
            TaskFilterChip(
                label = filter.label,
                icon = TaskBIcons.Filter,
                tint = TaskBTheme.colors.accent,
                onChange = { onIntent(TaskIntent.Choose(selection = TaskSelection.Filters)) },
                onClear = { onIntent(TaskIntent.Filter(filter = null)) },
                clearDescription = "Clear task filter",
            )
        }
    }
}

@Composable
private fun TaskFilterChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onChange: () -> Unit,
    onClear: () -> Unit,
    clearDescription: String,
) {
    val shape = RoundedCornerShape(size = 8.dp)
    Row(
        modifier = Modifier
            .height(height = 44.dp)
            .clip(shape = shape)
            .background(color = TaskBTheme.colors.selected),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(height = 44.dp)
                .clickable(onClick = onChange)
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size = 18.dp))
            Text(text = label, color = TaskBTheme.colors.accent, style = TaskBTheme.typography.button)
        }
        Box(
            modifier = Modifier
                .size(size = 44.dp)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TaskBIcons.Clear,
                contentDescription = clearDescription,
                tint = TaskBTheme.colors.accent,
                modifier = Modifier.size(size = 24.dp),
            )
        }
    }
}
