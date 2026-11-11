package com.mkulikov.taskb.ui.dialog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TaskBChoiceDialog(title: String, message: String, choices: List<TaskBDialogChoice>, onDismiss: () -> Unit) {
    TaskBDialogPanel(
        title = title,
        message = message,
        onDismiss = onDismiss,
        actionSpacing = 0.dp,
        actions = { TaskBDialogAction(label = "Cancel", onClick = onDismiss) },
    ) {
        if (choices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(height = 12.dp))
        }
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
