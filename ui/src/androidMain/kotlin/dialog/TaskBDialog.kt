package com.mkulikov.taskb.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun TaskBDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    if (LocalInspectionMode.current) {
        content()
        return
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        content = content,
    )
}
