package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.feature.group.preview.previewGroups
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Preview(name = "Edit group – wide", widthDp = 1280, heightDp = 680)
@Composable
private fun GroupEditorWidePreview() {
    val group = previewGroups.first()
    val state = remember {
        GroupEditorState(
            isNew = false,
            original = group,
            name = TextFieldState(initialText = group.name),
            color = group.color,
            isLoading = false,
        )
    }
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            GroupEditorWideScreen(state = state, onIntent = {}, onBack = {})
        }
    }
}

@Preview(name = "New group – compact", widthDp = 400, heightDp = 640)
@Composable
private fun GroupEditorCompactPreview() {
    val state = remember { GroupEditorState(isNew = true) }
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            GroupEditorCompactScreen(state = state, onIntent = {}, onBack = {})
        }
    }
}
