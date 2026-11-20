package com.mkulikov.taskb.feature.task.screen.task_creation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskCalendarVisibility
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskDateSection
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorIntent
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorState
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskRepeatEnd
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskRepeatOn
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskRepeatPattern
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun TaskCreationSchedule(state: TaskEditorState, onIntent: (TaskEditorIntent) -> Unit) {
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
                    CreationFieldLabel(text = "Options")
                    TaskCalendarVisibility(
                        state = state,
                        enabled = enabled,
                        onIntent = onIntent,
                        labelStyle = TaskBTheme.typography.body,
                    )
                }
                Spacer(modifier = Modifier.weight(weight = 1f))
            }
        }
    }
}
