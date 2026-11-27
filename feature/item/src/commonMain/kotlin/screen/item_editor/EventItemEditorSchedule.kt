package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun EventItemEditorSchedule(
    state: ItemEditorState,
    details: EventSchedule,
    onIntent: (ItemEditorIntent) -> Unit,
) {
    val changeRecurrence: (ItemRecurrence?) -> Unit = { rule ->
        onIntent(ItemEditorIntent.RecurrenceChanged(recurrence = rule))
    }
    val enabled = state.enabled
    Column(verticalArrangement = Arrangement.spacedBy(space = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
            Column(modifier = Modifier.weight(weight = 1f)) {
                EventDateSection(
                    state = details,
                    today = state.today,
                    enabled = enabled,
                    onIntent = onIntent,
                    end = false,
                )
            }
            Column(modifier = Modifier.weight(weight = 1f)) {
                EventDateSection(
                    state = details,
                    today = state.today,
                    enabled = enabled,
                    onIntent = onIntent,
                    end = true,
                )
            }
        }
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                Column(modifier = Modifier.weight(weight = 1f)) {
                    EventStartTimeField(state = details, onIntent = onIntent, enabled = enabled)
                }
                Column(modifier = Modifier.weight(weight = 1f)) {
                    if (details.startTime != null) {
                        EventEndTimeField(state = details, onIntent = onIntent, enabled = enabled)
                    }
                }
            }
            details.timingError?.let { error ->
                Spacer(modifier = Modifier.height(height = 8.dp))
                Text(text = error, style = TaskBTheme.typography.small, color = TaskBTheme.colors.danger)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
            ItemRepeatPattern(
                state = state.schedule,
                onChange = changeRecurrence,
                enabled = enabled,
                modifier = Modifier.weight(weight = 1f),
            )
            Box(modifier = Modifier.weight(weight = 1f)) {
                ItemRepeatOn(
                    state = state.schedule,
                    onChange = changeRecurrence,
                    enabled = enabled,
                    wide = true,
                    showHeading = true,
                )
            }
        }
        if (state.schedule.recurrence != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                ItemRepeatEnd(
                    state = state.schedule,
                    onChange = changeRecurrence,
                    onChooseEndDate = { onIntent(ItemEditorIntent.ChooseRepeatEndDate) },
                    enabled = enabled,
                    modifier = Modifier.weight(weight = 1f),
                )
                Spacer(modifier = Modifier.weight(weight = 1f))
            }
        }
    }
}
