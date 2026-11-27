package com.mkulikov.taskb.feature.item.screen.task_editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.item.component.taskDateLabel
import com.mkulikov.taskb.feature.item.screen.item_editor.ItemSelectionField
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun TaskOptionSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labelStyle: TextStyle = TaskBTheme.typography.body,
    minHeight: Dp = 48.dp,
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = labelStyle,
                color = TaskBTheme.colors.text,
            )
            Switch(
                checked = checked,
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
internal fun TaskDateSection(state: TaskEditorState, today: LocalDate, onIntent: (TaskEditorIntent) -> Unit) {
    val enabled = !state.isBusy && state.dialog == null
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Date",
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
            modifier = Modifier.height(height = 18.dp),
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        ItemSelectionField(
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
internal fun TaskGroupSection(
    group: Group?,
    enabled: Boolean,
    onChooseGroup: () -> Unit,
    onClearGroup: (() -> Unit)?,
    description: String,
    clearDescription: String,
) {
    val groupIcon = if (group == null) TaskBIcons.Folder else TaskBIcons.FolderFilled
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Group",
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
            modifier = Modifier.height(height = 18.dp),
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        ItemSelectionField(
            label = group?.name ?: "No group",
            icon = groupIcon,
            tint = group?.color?.color ?: TaskBTheme.colors.muted,
            placeholder = group == null,
            description = description,
            enabled = enabled,
            onClick = onChooseGroup,
            onClear = onClearGroup,
            clearDescription = clearDescription,
            height = 44.dp,
        )
    }
}
