package com.mkulikov.taskb.ui.dialog

import androidx.compose.runtime.Composable

@Composable
expect fun TaskBDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit)
