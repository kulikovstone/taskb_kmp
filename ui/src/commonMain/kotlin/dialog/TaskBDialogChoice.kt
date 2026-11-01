package com.mkulikov.taskb.ui.dialog

data class TaskBDialogChoice(val label: String, val onClick: () -> Unit, val destructive: Boolean = false)
