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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.utils.time.dateLabel
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.component.TaskBTextArea
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun EventEditorForm(
    state: EventEditorState,
    groups: List<Group>,
    today: LocalDate,
    onIntent: (EventEditorIntent) -> Unit,
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
                    EventNameSection(state = state, onIntent = onIntent, enabled = enabled)
                }
                Column(modifier = Modifier.weight(weight = 1f)) {
                    EventGroupSection(
                        state = state,
                        groups = groups,
                        onIntent = onIntent,
                        onChooseGroup = onChooseGroup,
                    )
                }
            }
        } else {
            EventNameSection(state = state, onIntent = onIntent, enabled = enabled)
            Spacer(modifier = Modifier.height(height = 24.dp))
            EventGroupSection(state = state, groups = groups, onIntent = onIntent, onChooseGroup = onChooseGroup)
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
            description = "Event description",
            placeholder = "Add a description",
            enabled = enabled,
            minLines = if (wide) 2 else 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(height = 24.dp))
        if (wide) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                Column(modifier = Modifier.weight(weight = 1f)) {
                    EventDateSection(state = state, today = today, onIntent = onIntent, end = false)
                }
                Column(modifier = Modifier.weight(weight = 1f)) {
                    EventDateSection(state = state, today = today, onIntent = onIntent, end = true)
                }
            }
        } else {
            EventDateSection(state = state, today = today, onIntent = onIntent, end = false)
            Spacer(modifier = Modifier.height(height = 24.dp))
            EventDateSection(state = state, today = today, onIntent = onIntent, end = true)
        }
        Spacer(modifier = Modifier.height(height = 24.dp))
        EventTimeFields(state = state, onIntent = onIntent, enabled = enabled, wide = wide)
        state.timingError?.let { error ->
            Spacer(modifier = Modifier.height(height = 8.dp))
            Text(text = error, style = TaskBTheme.typography.small, color = TaskBTheme.colors.danger)
        }
        Spacer(modifier = Modifier.height(height = 24.dp))
        EventRecurrenceFields(state = state, onIntent = onIntent, enabled = enabled, wide = wide)
        if (state.original?.recurrence != null) {
            Spacer(modifier = Modifier.height(height = 16.dp))
            Text(
                text = "Name, description and group apply to every occurrence.",
                style = TaskBTheme.typography.small,
                color = TaskBTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun EventNameSection(state: EventEditorState, onIntent: (EventEditorIntent) -> Unit, enabled: Boolean) {
    Text(
        text = "Event name",
        style = TaskBTheme.typography.label,
        color = TaskBTheme.colors.text,
        modifier = Modifier.height(height = 18.dp),
    )
    Spacer(modifier = Modifier.height(height = 8.dp))
    TaskBTextField(
        state = state.name,
        description = "Event name",
        placeholder = "Enter event name",
        enabled = enabled,
        autoFocus = true,
        onDone = { if (state.canSave) onIntent(EventEditorIntent.Save) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun EventDateSection(
    state: EventEditorState,
    today: LocalDate,
    onIntent: (EventEditorIntent) -> Unit,
    end: Boolean,
) {
    val enabled = !state.isBusy && state.dialog == null
    val date = if (end) state.endDate else state.dueDate
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (end) "End date" else "Date",
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
            modifier = Modifier.height(height = 18.dp),
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        EventSelectionField(
            label = dateLabel(date = date, today = today, includeDate = true),
            icon = TaskBIcons.Calendar,
            tint = TaskBTheme.colors.muted,
            placeholder = false,
            description = if (end) "Choose event end date" else "Choose date",
            enabled = enabled,
            onClick = { onIntent(if (end) EventEditorIntent.ChooseEventEndDate else EventEditorIntent.ChooseDate) },
            onClear = null,
            clearDescription = "",
        )
        if (!end) {
            Spacer(modifier = Modifier.height(height = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                TaskBSelectionButton(
                    label = "Today",
                    selected = state.dueDate == today,
                    onClick = { onIntent(EventEditorIntent.DateChanged(date = today)) },
                    modifier = Modifier.weight(weight = 1f),
                    enabled = enabled,
                )
                TaskBSelectionButton(
                    label = "Tomorrow",
                    selected = state.dueDate == today.plus(value = 1, unit = DateTimeUnit.DAY),
                    onClick = {
                        onIntent(
                            EventEditorIntent.DateChanged(date = today.plus(value = 1, unit = DateTimeUnit.DAY))
                        )
                    },
                    modifier = Modifier.weight(weight = 1f),
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun EventGroupSection(
    state: EventEditorState,
    groups: List<Group>,
    onIntent: (EventEditorIntent) -> Unit,
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
        EventSelectionField(
            label = group?.name ?: "No group",
            icon = groupIcon,
            tint = group?.color?.color ?: TaskBTheme.colors.muted,
            placeholder = group == null,
            description = "Choose event group",
            enabled = enabled,
            onClick = onChooseGroup,
            onClear = state.groupId?.let { { onIntent(EventEditorIntent.GroupChanged(id = null)) } },
            clearDescription = "Clear event group",
            height = 44.dp,
        )
    }
}

@Composable
internal fun EventSelectionField(
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
