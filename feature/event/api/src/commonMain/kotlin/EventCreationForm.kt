package com.mkulikov.taskb.feature.event.api

import androidx.compose.runtime.Composable

class EventCreationForm(
    val canSave: Boolean,
    val isSaving: Boolean,
    val enabled: Boolean,
    val onSave: () -> Unit,
    val schedule: @Composable () -> Unit,
)
