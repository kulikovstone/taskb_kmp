package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Preview(name = "New event – wide", widthDp = 1280, heightDp = 900)
@Composable
private fun EventEditorWidePreview() = EventEditorPreview(wide = true)

@Preview(name = "New event – compact", widthDp = 400, heightDp = 900)
@Composable
private fun EventEditorCompactPreview() = EventEditorPreview(wide = false)

@Composable
private fun EventEditorPreview(wide: Boolean) {
    val state = remember {
        EventEditorState(
            name = TextFieldState(initialText = "Team meeting"),
            description = TextFieldState(initialText = "Discuss the release"),
            dueDate = LocalDate(year = 2026, month = 11, day = 5),
            today = LocalDate(year = 2026, month = 11, day = 5),
            isLoadingGroups = false,
        )
    }
    EventEditorPreview(state = state, wide = wide)
}

@Preview(name = "Timed repeating event", widthDp = 1280, heightDp = 1100)
@Composable
private fun EventEditorTimedPreview() {
    val state = remember {
        val date = LocalDate(year = 2026, month = 11, day = 8)
        EventEditorState(
            name = TextFieldState(initialText = "Planning meeting"),
            description = TextFieldState(initialText = "Review the plan and agree on next steps."),
            today = date,
            startTime = LocalTime(hour = 11, minute = 30),
            endTime = LocalTime(hour = 12, minute = 30),
            recurrence = EventRecurrence.monthly(date = date),
            repeatDay = TextFieldState(initialText = date.day.toString()),
            isLoadingGroups = false,
        )
    }
    EventEditorPreview(state = state, wide = true)
}

@Composable
private fun EventEditorPreview(state: EventEditorState, wide: Boolean) {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            EventEditorContent(state = state, onIntent = {}, wide = wide, onBack = {}, onChooseGroup = {})
        }
    }
}

@Preview(name = "Time picker – clock", widthDp = 420, heightDp = 640)
@Composable
private fun EventTimePickerClockPreview() = EventTimePickerPreview(availableHeight = 640.dp)

@Preview(name = "Time picker – keyboard", widthDp = 400, heightDp = 340)
@Composable
private fun EventTimePickerKeyboardPreview() = EventTimePickerPreview(availableHeight = 340.dp)

@Composable
private fun EventTimePickerPreview(availableHeight: Dp) {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.subtle) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EventTimePickerDialog(
                    title = "Choose time",
                    initialTime = LocalTime(hour = 11, minute = 30),
                    availableHeight = availableHeight,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
}
