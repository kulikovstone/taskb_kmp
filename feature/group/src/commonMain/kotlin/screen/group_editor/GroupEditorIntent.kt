package com.mkulikov.taskb.feature.group.screen.group_editor

import com.mkulikov.taskb.feature.group.api.model.GroupColor

internal sealed interface GroupEditorIntent {
    data class ColorChanged(val color: GroupColor) : GroupEditorIntent
    data object Save : GroupEditorIntent
    data object PrepareDelete : GroupEditorIntent
    data object ConfirmDelete : GroupEditorIntent
    data object DismissDialog : GroupEditorIntent
    data object RetryLoad : GroupEditorIntent
}
