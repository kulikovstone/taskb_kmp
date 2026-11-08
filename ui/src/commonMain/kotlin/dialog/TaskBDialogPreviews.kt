package com.mkulikov.taskb.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Preview(name = "Confirmation dialog", widthDp = 460, heightDp = 208)
@Composable
private fun TaskBConfirmationDialogPreview() {
    TaskBDialogPreview(darkTheme = false) {
        TaskBConfirmationDialog(
            title = "Delete group",
            message = "The group will be deleted. Its tasks will be kept.",
            confirmText = "Delete",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Choice dialog", widthDp = 460, heightDp = 304)
@Composable
private fun TaskBChoiceDialogPreview() {
    TaskBDialogPreview(darkTheme = false) {
        TaskBChoiceDialog(
            title = "Move repeating event",
            message = "Choose which occurrences to move.",
            choices = listOf(
                TaskBDialogChoice(label = "Only this event", onClick = {}),
                TaskBDialogChoice(label = "This and following events", onClick = {}),
            ),
            onDismiss = {},
        )
    }
}

@Preview(name = "Destructive choice – compact", widthDp = 320, heightDp = 408)
@Composable
private fun TaskBDestructiveChoiceDialogPreview() {
    TaskBDialogPreview(darkTheme = true) {
        TaskBChoiceDialog(
            title = "Delete repeating task",
            message = "Choose which events to delete. Deleting the entire task also removes its completed history.",
            choices = listOf(
                TaskBDialogChoice(label = "Only this event", onClick = {}, destructive = true),
                TaskBDialogChoice(label = "This and following events", onClick = {}, destructive = true),
                TaskBDialogChoice(label = "Entire task and all events", onClick = {}, destructive = true),
            ),
            onDismiss = {},
        )
    }
}

@Composable
private fun TaskBDialogPreview(darkTheme: Boolean, content: @Composable () -> Unit) {
    TaskBTheme(darkTheme = darkTheme) {
        Surface(color = TaskBTheme.colors.subtle) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}
