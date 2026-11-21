package com.mkulikov.taskb.feature.task.screen.item_editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskDateSection
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorIntent
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorState
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskOptionSwitch
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskRepeatEnd
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskRepeatOn
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskRepeatPattern

@Composable
internal fun TaskItemEditorSchedule(state: TaskEditorState, onIntent: (TaskEditorIntent) -> Unit) {
    val enabled = !state.isBusy && !state.isFinished && state.dialog == null
    Column(verticalArrangement = Arrangement.spacedBy(space = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
            Column(modifier = Modifier.weight(weight = 1f)) {
                TaskDateSection(state = state, today = state.today, onIntent = onIntent)
            }
            Box(modifier = Modifier.weight(weight = 1f)) {
                TaskRepeatPattern(state = state, onIntent = onIntent, enabled = enabled)
            }
        }
        if (state.recurrence != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                TaskRepeatOn(
                    state = state,
                    onIntent = onIntent,
                    enabled = enabled,
                    wide = true,
                    showHeading = true,
                    modifier = Modifier.weight(weight = 1f),
                )
                TaskRepeatEnd(
                    state = state,
                    onIntent = onIntent,
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
                        checked = state.showInCalendar,
                        enabled = enabled,
                        minHeight = 36.dp,
                        onCheckedChange = { checked ->
                            onIntent(TaskEditorIntent.CalendarVisibilityChanged(showInCalendar = checked))
                        },
                    )
                    TaskOptionSwitch(
                        label = "Quick delete",
                        checked = state.quickDelete,
                        enabled = enabled,
                        minHeight = 36.dp,
                        onCheckedChange = { checked ->
                            onIntent(TaskEditorIntent.QuickDeleteChanged(enabled = checked))
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(weight = 1f))
            }
        }
    }
}
