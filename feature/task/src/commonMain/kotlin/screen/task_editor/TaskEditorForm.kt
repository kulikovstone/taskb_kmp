package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalRippleConfiguration
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RippleConfiguration
import androidx.compose.material.RippleDefaults
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.component.taskDateLabel
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.component.TaskBTextArea
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun TaskEditorForm(
    state: TaskEditorState,
    groups: List<Group>,
    today: LocalDate,
    onIntent: (TaskEditorIntent) -> Unit,
    onChooseGroup: () -> Unit,
    wide: Boolean,
    modifier: Modifier,
) {
    val enabled = !state.isBusy && state.dialog == null
    Column(
        modifier = modifier
            .verticalScroll(state = rememberScrollState())
            .padding(horizontal = if (wide) 0.dp else 24.dp, vertical = if (wide) 32.dp else 24.dp)
    ) {
        if (wide) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                Column(modifier = Modifier.weight(weight = 1f)) {
                    TaskNameSection(state = state, onIntent = onIntent, enabled = enabled)
                }
                Column(modifier = Modifier.weight(weight = 1f)) {
                    TaskGroupSection(
                        state = state,
                        groups = groups,
                        onIntent = onIntent,
                        onChooseGroup = onChooseGroup,
                    )
                }
            }
        } else {
            TaskNameSection(state = state, onIntent = onIntent, enabled = enabled)
            Spacer(modifier = Modifier.height(height = 24.dp))
            TaskGroupSection(state = state, groups = groups, onIntent = onIntent, onChooseGroup = onChooseGroup)
        }
        Spacer(modifier = Modifier.height(height = 24.dp))
        Text(
            text = "Description",
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
            modifier = Modifier.height(height = 18.dp),
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        TaskBTextArea(
            state = state.description,
            description = "Task description",
            placeholder = "Add a description",
            enabled = enabled,
            minLines = if (wide) 2 else 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(height = 24.dp))
        if (wide) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                Column(modifier = Modifier.weight(weight = 1f)) {
                    TaskDateSection(state = state, today = today, onIntent = onIntent)
                }
                TaskCalendarVisibility(
                    state = state,
                    enabled = enabled,
                    onIntent = onIntent,
                    modifier = Modifier.weight(weight = 1f),
                )
            }
        } else {
            TaskDateSection(state = state, today = today, onIntent = onIntent)
            Spacer(modifier = Modifier.height(height = 16.dp))
            TaskCalendarVisibility(
                state = state,
                enabled = enabled,
                onIntent = onIntent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.dueDate != null) {
            Spacer(modifier = Modifier.height(height = 24.dp))
            TaskRecurrenceFields(state = state, onIntent = onIntent, enabled = enabled, wide = wide)
        }
        if (state.original?.recurrence != null) {
            Spacer(modifier = Modifier.height(height = 16.dp))
            Text(
                text = "Name, description, group and calendar visibility apply to all occurrences. " +
                    "Schedule changes preserve completed history.",
                style = TaskBTheme.typography.small,
                color = TaskBTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun TaskCalendarVisibility(
    state: TaskEditorState,
    enabled: Boolean,
    onIntent: (TaskEditorIntent) -> Unit,
    modifier: Modifier,
) {
    val defaultAlpha = RippleDefaults.rippleAlpha(
        contentColor = LocalContentColor.current,
        lightTheme = MaterialTheme.colors.isLight,
    )
    val ripple = RippleConfiguration(
        rippleAlpha = RippleAlpha(
            draggedAlpha = defaultAlpha.draggedAlpha,
            focusedAlpha = defaultAlpha.focusedAlpha,
            hoveredAlpha = 0f,
            pressedAlpha = defaultAlpha.pressedAlpha,
        ),
    )
    CompositionLocalProvider(value = LocalRippleConfiguration provides ripple) {
        Row(
            modifier = modifier
                .heightIn(min = 48.dp)
                .toggleable(
                    value = state.showInCalendar,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { checked ->
                        onIntent(TaskEditorIntent.CalendarVisibilityChanged(showInCalendar = checked))
                    },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Show in calendar",
                style = TaskBTheme.typography.label,
                color = TaskBTheme.colors.text,
            )
            Switch(
                checked = state.showInCalendar,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TaskBTheme.colors.accent,
                    checkedTrackColor = TaskBTheme.colors.accent,
                    uncheckedThumbColor = TaskBTheme.colors.muted,
                    uncheckedTrackColor = TaskBTheme.colors.muted,
                ),
            )
        }
    }
}

@Composable
private fun TaskNameSection(state: TaskEditorState, onIntent: (TaskEditorIntent) -> Unit, enabled: Boolean) {
    Text(
        text = "Task name",
        style = TaskBTheme.typography.label,
        color = TaskBTheme.colors.text,
        modifier = Modifier.height(height = 18.dp),
    )
    Spacer(modifier = Modifier.height(height = 8.dp))
    TaskBTextField(
        state = state.name,
        description = "Task name",
        placeholder = "Enter task name",
        enabled = enabled,
        autoFocus = true,
        onDone = { if (state.canSave) onIntent(TaskEditorIntent.Save) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TaskDateSection(state: TaskEditorState, today: LocalDate, onIntent: (TaskEditorIntent) -> Unit) {
    val enabled = !state.isBusy && state.dialog == null
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Date",
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
            modifier = Modifier.height(height = 18.dp),
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        TaskSelectionField(
            label = state.dueDate?.let { date -> taskDateLabel(
                date = date,
                today = today,
                includeDate = true,
            ) } ?: "Choose date",
            icon = TaskBIcons.Calendar,
            tint = TaskBTheme.colors.muted,
            placeholder = state.dueDate == null,
            description = "Choose date",
            enabled = enabled,
            onClick = { onIntent(TaskEditorIntent.ChooseDate) },
            onClear = state.dueDate?.let { { onIntent(TaskEditorIntent.DateChanged(date = null)) } },
            clearDescription = "Clear date",
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            TaskBSelectionButton(
                label = "Today",
                selected = state.dueDate == today,
                onClick = { onIntent(TaskEditorIntent.DateChanged(date = today)) },
                modifier = Modifier.weight(weight = 1f),
                enabled = enabled,
            )
            TaskBSelectionButton(
                label = "Tomorrow",
                selected = state.dueDate == today.plus(value = 1, unit = DateTimeUnit.DAY),
                onClick = {
                    onIntent(TaskEditorIntent.DateChanged(date = today.plus(value = 1, unit = DateTimeUnit.DAY)))
                },
                modifier = Modifier.weight(weight = 1f),
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun TaskGroupSection(
    state: TaskEditorState,
    groups: List<Group>,
    onIntent: (TaskEditorIntent) -> Unit,
    onChooseGroup: () -> Unit,
) {
    val enabled = !state.isBusy && state.dialog == null
    val group = groups.find { group -> group.id == state.groupId }
    val groupIcon = if (group == null) TaskBIcons.Folder else TaskBIcons.FolderFilled
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Group",
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
            modifier = Modifier.height(height = 18.dp),
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        TaskSelectionField(
            label = group?.name ?: "No group",
            icon = groupIcon,
            tint = group?.color?.color ?: TaskBTheme.colors.muted,
            placeholder = group == null,
            description = "Choose task group",
            enabled = enabled,
            onClick = onChooseGroup,
            onClear = state.groupId?.let { { onIntent(TaskEditorIntent.GroupChanged(id = null)) } },
            clearDescription = "Clear task group",
            height = 44.dp,
        )
    }
}

@Composable
internal fun TaskSelectionField(
    label: String,
    icon: ImageVector,
    tint: Color,
    placeholder: Boolean,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    clearDescription: String,
    height: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(size = 8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = height)
            .clip(shape = shape)
            .border(width = 1.dp, color = TaskBTheme.colors.border, shape = shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxHeight()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size = 24.dp))
            Text(
                text = label,
                color = if (placeholder) TaskBTheme.colors.muted else TaskBTheme.colors.text,
                style = TaskBTheme.typography.body,
                modifier = Modifier.weight(weight = 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = TaskBIcons.ChevronRight,
                contentDescription = null,
                tint = TaskBTheme.colors.muted,
                modifier = Modifier.size(size = 18.dp),
            )
        }
        onClear?.let { clear ->
            Box(
                modifier = Modifier
                    .width(width = 44.dp)
                    .fillMaxHeight()
                    .clickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = clear,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TaskBIcons.Clear,
                    contentDescription = clearDescription,
                    tint = TaskBTheme.colors.muted,
                )
            }
        }
    }
}
