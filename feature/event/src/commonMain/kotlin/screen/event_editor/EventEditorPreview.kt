package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate

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
    TaskBTheme(darkTheme = false) {
        EventEditorContent(state = state, onIntent = {}, wide = wide, onBack = {}, onChooseGroup = {})
    }
}
