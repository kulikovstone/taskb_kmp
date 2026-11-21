package com.mkulikov.taskb.feature.event.api

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface IEventItemEditor {
    @Composable
    fun Content(
        availableHeight: Dp,
        onFinished: () -> Unit,
        name: TextFieldState? = null,
        description: TextFieldState? = null,
        initialDate: LocalDate? = null,
        groupId: Int? = null,
        edit: EventEditorRoute? = null,
        groupResults: Flow<GroupSelectionResult>? = null,
        render: @Composable (EventItemEditorForm) -> Unit,
    )
}
