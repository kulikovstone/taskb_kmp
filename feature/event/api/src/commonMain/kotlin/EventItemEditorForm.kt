package com.mkulikov.taskb.feature.event.api

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.mkulikov.taskb.feature.group.api.model.Group

class EventItemEditorForm(
    val name: TextFieldState,
    val description: TextFieldState,
    val group: Group?,
    val isLoading: Boolean,
    val loadError: String?,
    val canSave: Boolean,
    val isSaving: Boolean,
    val enabled: Boolean,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onRetry: () -> Unit,
    val onClearGroup: () -> Unit,
    val schedule: @Composable () -> Unit,
)
