package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.item.component.taskDateLabel
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun TaskDateSection(
    state: TaskSchedule,
    today: LocalDate,
    enabled: Boolean,
    onIntent: (ItemEditorIntent) -> Unit,
) {
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
            onClick = { onIntent(ItemEditorIntent.ChooseDate) },
            onClear = state.dueDate?.let { { onIntent(ItemEditorIntent.DateChanged(date = null)) } },
            clearDescription = "Clear date",
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            TaskBSelectionButton(
                label = "Today",
                selected = state.dueDate == today,
                onClick = { onIntent(ItemEditorIntent.DateChanged(date = today)) },
                modifier = Modifier.weight(weight = 1f),
                enabled = enabled,
            )
            TaskBSelectionButton(
                label = "Tomorrow",
                selected = state.dueDate == today.plus(value = 1, unit = DateTimeUnit.DAY),
                onClick = {
                    onIntent(ItemEditorIntent.DateChanged(date = today.plus(value = 1, unit = DateTimeUnit.DAY)))
                },
                modifier = Modifier.weight(weight = 1f),
                enabled = enabled,
            )
        }
    }
}
