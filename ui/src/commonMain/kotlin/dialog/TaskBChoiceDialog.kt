package com.mkulikov.taskb.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
fun TaskBChoiceDialog(title: String, message: String, choices: List<TaskBDialogChoice>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = TaskBTheme.typography.section) },
        text = {
            Column {
                Text(text = message, style = TaskBTheme.typography.body)
                choices.forEach { choice ->
                    TextButton(onClick = choice.onClick, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = choice.label,
                            color = if (choice.destructive) TaskBTheme.colors.danger else TaskBTheme.colors.accent,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
    )
}
