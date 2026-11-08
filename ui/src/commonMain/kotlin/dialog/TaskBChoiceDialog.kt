package com.mkulikov.taskb.ui.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TaskBChoiceDialog(title: String, message: String, choices: List<TaskBDialogChoice>, onDismiss: () -> Unit) {
    TaskBDialogPanel(
        title = title,
        message = message,
        onDismiss = onDismiss,
        cornerRadius = 4.dp,
        actionSpacing = 0.dp,
        actions = { TaskBDialogAction(label = "Cancel", onClick = onDismiss) },
    ) {
        choices.forEach { choice ->
            TaskBDialogAction(
                label = choice.label,
                onClick = choice.onClick,
                modifier = Modifier.fillMaxWidth(),
                destructive = choice.destructive,
            )
        }
    }
}
