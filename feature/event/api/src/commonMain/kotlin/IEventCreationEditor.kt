package com.mkulikov.taskb.feature.event.api

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.LocalDate

interface IEventCreationEditor {
    @Composable
    fun Content(
        name: TextFieldState,
        description: TextFieldState,
        initialDate: LocalDate?,
        groupId: Int?,
        availableHeight: Dp,
        onSaved: () -> Unit,
        render: @Composable (EventCreationForm) -> Unit,
    )
}
