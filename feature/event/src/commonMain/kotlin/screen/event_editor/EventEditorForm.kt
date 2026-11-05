package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBTextArea
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.core.utils.time.dateLabel
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun EventEditorForm(state: EventEditorState, groups: List<Group>, today: LocalDate, onIntent: (EventEditorIntent) -> Unit, onChooseGroup: () -> Unit, wide: Boolean, modifier: Modifier) {
    val enabled = !state.isBusy && state.dialog == null
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 0.dp else 24.dp, vertical = if (wide) 32.dp else 24.dp)) {
        Text("Event name", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(8.dp))
        TaskBTextField(state.name, "Event name", placeholder = "Enter event name", enabled = enabled,
            autoFocus = true, onDone = { if (state.canSave) onIntent(EventEditorIntent.Save) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text("Description", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(8.dp))
        TaskBTextArea(state.description, "Event description", placeholder = "Add a description", enabled = enabled,
            minLines = if (wide) 2 else 4, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) { EventDateSection(state, today, onIntent) }
            Column(Modifier.weight(1f)) { EventGroupSection(state, groups, onIntent, onChooseGroup) }
        } else {
            EventDateSection(state, today, onIntent)
            Spacer(Modifier.height(24.dp))
            EventGroupSection(state, groups, onIntent, onChooseGroup)
        }
        if (state.dueDate != null) {
            Spacer(Modifier.height(24.dp))
            EventTimeFields(state, onIntent, enabled, wide)
            Spacer(Modifier.height(24.dp))
            EventRecurrenceFields(state, onIntent, enabled, wide)
        }
        if (state.original?.recurrence != null) {
            Spacer(Modifier.height(16.dp))
            Text("Name, description and group apply to every occurrence.", style = TaskBTheme.typography.small, color = TaskBTheme.colors.muted)
        }
    }
}

@Composable
private fun EventDateSection(state: EventEditorState, today: LocalDate, onIntent: (EventEditorIntent) -> Unit) {
    val enabled = !state.isBusy && state.dialog == null
    Column(Modifier.fillMaxWidth()) {
        Text("Date", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(8.dp))
        EventSelectionField(state.dueDate?.let { dateLabel(it, today, true) } ?: "Choose date", TaskBIcons.Calendar, TaskBTheme.colors.muted,
            placeholder = state.dueDate == null, description = "Choose date", enabled = enabled,
            onClick = { onIntent(EventEditorIntent.ChooseDate) }, onClear = state.dueDate?.let { { onIntent(EventEditorIntent.DateChanged(null)) } }, clearDescription = "Clear date")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskBSelectionButton("Today", state.dueDate == today, { onIntent(EventEditorIntent.DateChanged(today)) }, Modifier.weight(1f), enabled)
            TaskBSelectionButton("Tomorrow", state.dueDate == today.plus(1, DateTimeUnit.DAY), { onIntent(EventEditorIntent.DateChanged(today.plus(1, DateTimeUnit.DAY))) }, Modifier.weight(1f), enabled)
        }
    }
}

@Composable
private fun EventGroupSection(state: EventEditorState, groups: List<Group>, onIntent: (EventEditorIntent) -> Unit, onChooseGroup: () -> Unit) {
    val enabled = !state.isBusy && state.dialog == null
    val group = groups.find { it.id == state.groupId }
    val groupIcon = if (group == null) TaskBIcons.Folder else TaskBIcons.FolderFilled
    Column(Modifier.fillMaxWidth()) {
        Text("Group", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(8.dp))
        EventSelectionField(group?.name ?: "No group", groupIcon, group?.color?.color ?: TaskBTheme.colors.muted,
            placeholder = group == null, description = "Choose event group", enabled = enabled, onClick = onChooseGroup,
            onClear = state.groupId?.let { { onIntent(EventEditorIntent.GroupChanged(null)) } }, clearDescription = "Clear event group")
    }
}

@Composable
internal fun EventSelectionField(label: String, icon: ImageVector, tint: Color, placeholder: Boolean, description: String, enabled: Boolean, onClick: () -> Unit, onClear: (() -> Unit)?, clearDescription: String) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.fillMaxWidth().height(56.dp).clip(shape).border(1.dp, TaskBTheme.colors.border, shape), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Text(label, color = if (placeholder) TaskBTheme.colors.muted else TaskBTheme.colors.text, style = TaskBTheme.typography.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(TaskBIcons.ChevronRight, null, tint = TaskBTheme.colors.muted, modifier = Modifier.size(18.dp))
        }
        onClear?.let { clear ->
            Box(
                Modifier.width(44.dp).fillMaxHeight().clickable(
                    enabled = enabled, interactionSource = null, indication = null, role = Role.Button, onClick = clear,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TaskBIcons.Clear, clearDescription, tint = TaskBTheme.colors.muted)
            }
        }
    }
}
