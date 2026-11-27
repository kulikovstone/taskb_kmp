package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence

@Composable
internal fun TaskItemEditorSchedule(
    state: ItemEditorState,
    details: TaskSchedule,
    onIntent: (ItemEditorIntent) -> Unit,
) {
    val changeRecurrence: (ItemRecurrence?) -> Unit = { rule ->
        onIntent(ItemEditorIntent.RecurrenceChanged(recurrence = rule))
    }
    val enabled = state.enabled
    Column(verticalArrangement = Arrangement.spacedBy(space = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
            Column(modifier = Modifier.weight(weight = 1f)) {
                TaskDateSection(state = details, today = state.today, enabled = enabled, onIntent = onIntent)
            }
            Box(modifier = Modifier.weight(weight = 1f)) {
                ItemRepeatPattern(state = state.schedule, onChange = changeRecurrence, enabled = enabled)
            }
        }
        if (state.schedule.recurrence != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                ItemRepeatOn(
                    state = state.schedule,
                    onChange = changeRecurrence,
                    enabled = enabled,
                    wide = true,
                    showHeading = true,
                    modifier = Modifier.weight(weight = 1f),
                )
                ItemRepeatEnd(
                    state = state.schedule,
                    onChange = changeRecurrence,
                    onChooseEndDate = { onIntent(ItemEditorIntent.ChooseRepeatEndDate) },
                    enabled = enabled,
                    modifier = Modifier.weight(weight = 1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                Column(modifier = Modifier.weight(weight = 1f)) {
                    ItemEditorLabel(text = "Options")
                    Spacer(modifier = Modifier.height(height = 6.dp))
                    TaskOptionSwitch(
                        label = "Show in calendar",
                        checked = details.showInCalendar,
                        enabled = enabled,
                        minHeight = 36.dp,
                        onCheckedChange = { checked ->
                            onIntent(ItemEditorIntent.CalendarVisibilityChanged(showInCalendar = checked))
                        },
                    )
                    TaskOptionSwitch(
                        label = "Quick delete",
                        checked = details.quickDelete,
                        enabled = enabled,
                        minHeight = 36.dp,
                        onCheckedChange = { checked ->
                            onIntent(ItemEditorIntent.QuickDeleteChanged(enabled = checked))
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(weight = 1f))
            }
        }
    }
}
