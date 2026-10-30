package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.feature.task.component.taskDateLabel
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun TaskEditorForm(state: TaskEditorState, groups: List<Group>, today: LocalDate, onIntent: (TaskEditorIntent) -> Unit, onChooseGroup: () -> Unit, wide: Boolean, modifier: Modifier) {
    val enabled = !state.isBusy && state.dialog == null
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 0.dp else 24.dp, vertical = if (wide) 32.dp else 24.dp)) {
        Text("Task name", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(8.dp))
        TaskBTextField(state.name, "Task name", placeholder = "Enter task name", enabled = enabled,
            autoFocus = true, onDone = { if (state.canSave) onIntent(TaskEditorIntent.Save) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) { TaskDateSection(state, today, onIntent) }
            Column(Modifier.weight(1f)) { TaskGroupSection(state, groups, onIntent, onChooseGroup) }
        } else {
            TaskDateSection(state, today, onIntent)
            Spacer(Modifier.height(24.dp))
            TaskGroupSection(state, groups, onIntent, onChooseGroup)
        }
    }
}

@Composable
private fun TaskDateSection(state: TaskEditorState, today: LocalDate, onIntent: (TaskEditorIntent) -> Unit) {
    val enabled = !state.isBusy && state.dialog == null
    Column(Modifier.fillMaxWidth()) {
        Text("Date", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(8.dp))
        TaskSelectionField(state.dueDate?.let { taskDateLabel(it, today, true) } ?: "Choose date", TaskBIcons.Calendar, TaskBTheme.colors.muted,
            placeholder = state.dueDate == null, description = "Choose date", enabled = enabled,
            onClick = { onIntent(TaskEditorIntent.ChooseDate) }, onClear = state.dueDate?.let { { onIntent(TaskEditorIntent.DateChanged(null)) } }, clearDescription = "Clear date")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickDate("Today", state.dueDate == today, enabled, { onIntent(TaskEditorIntent.DateChanged(today)) }, Modifier.weight(1f))
            QuickDate("Tomorrow", state.dueDate == today.plus(1, DateTimeUnit.DAY), enabled, { onIntent(TaskEditorIntent.DateChanged(today.plus(1, DateTimeUnit.DAY))) }, Modifier.weight(1f))
        }
        if (state.dueDate != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickDate("day", false, enabled, { onIntent(TaskEditorIntent.ShiftDate(days = 1)) }, Modifier.weight(1f), true)
                QuickDate("week", false, enabled, { onIntent(TaskEditorIntent.ShiftDate(days = 7)) }, Modifier.weight(1f), true)
                QuickDate("month", false, enabled, { onIntent(TaskEditorIntent.ShiftDate(months = 1)) }, Modifier.weight(1f), true)
            }
        }
    }
}

@Composable
private fun TaskGroupSection(state: TaskEditorState, groups: List<Group>, onIntent: (TaskEditorIntent) -> Unit, onChooseGroup: () -> Unit) {
    val enabled = !state.isBusy && state.dialog == null
    val group = groups.find { it.id == state.groupId }
    Column(Modifier.fillMaxWidth()) {
        Text("Group", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(8.dp))
        TaskSelectionField(group?.name ?: "No group", TaskBIcons.FolderFilled, group?.color?.color ?: TaskBTheme.colors.muted,
            placeholder = group == null, description = "Choose task group", enabled = enabled, onClick = onChooseGroup,
            onClear = state.groupId?.let { { onIntent(TaskEditorIntent.GroupChanged(null)) } }, clearDescription = "Clear task group")
    }
}

@Composable
private fun TaskSelectionField(label: String, icon: ImageVector, tint: Color, placeholder: Boolean, description: String, enabled: Boolean, onClick: () -> Unit, onClear: (() -> Unit)?, clearDescription: String) {
    Row(Modifier.fillMaxWidth().height(56.dp).border(1.dp, TaskBTheme.colors.border, RoundedCornerShape(8.dp)), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Text(label, color = if (placeholder) TaskBTheme.colors.muted else TaskBTheme.colors.text, style = TaskBTheme.typography.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(TaskBIcons.ChevronRight, null, tint = TaskBTheme.colors.muted, modifier = Modifier.size(18.dp))
        }
        onClear?.let { IconButton(it, enabled = enabled, modifier = Modifier.width(44.dp).fillMaxHeight()) { Icon(TaskBIcons.Clear, clearDescription, tint = TaskBTheme.colors.muted) } }
    }
}

@Composable
private fun QuickDate(label: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier, add: Boolean = false) {
    Row(modifier.height(44.dp).background(if (isSelected) TaskBTheme.colors.selected else TaskBTheme.colors.surface, RoundedCornerShape(8.dp))
        .border(1.dp, if (isSelected) TaskBTheme.colors.accent else TaskBTheme.colors.border, RoundedCornerShape(8.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { selected = isSelected; if (add) contentDescription = "Add one $label" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (add) { Icon(TaskBIcons.Add, null, tint = TaskBTheme.colors.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, style = TaskBTheme.typography.button, color = TaskBTheme.colors.accent)
    }
}
