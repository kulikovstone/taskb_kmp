package com.mkulikov.taskb.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
actual fun TaskBDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, scrimColor = TaskBTheme.colors.scrim),
        content = content,
    )
}
