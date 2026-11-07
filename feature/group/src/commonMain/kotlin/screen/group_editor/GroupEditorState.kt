package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor

internal data class GroupEditorState(
    val isNew: Boolean,
    val original: Group? = null,
    // Keep text, selection and IME composition synchronous, outside the Store's intent queue.
    val name: TextFieldState = TextFieldState(),
    val color: GroupColor = GroupColor.Grey,
    val isLoading: Boolean = !isNew,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isFinished: Boolean = false,
    val loadError: String? = null,
    val dialog: GroupEditorDialog? = null,
) {
    val isBusy: Boolean
        get() = isLoading || isSaving || isDeleting

    val normalizedName: String
        get() = name.text.toString().trim()

    val canSave: Boolean
        get() = !isBusy &&
            !isFinished &&
            loadError == null &&
            normalizedName.isNotEmpty() &&
            (isNew || original?.let { group -> group.name != normalizedName || group.color != color } == true)
}

internal sealed interface GroupEditorDialog {
    data object NameConflict : GroupEditorDialog

    data object ConfirmDelete : GroupEditorDialog

    data class Error(val message: String) : GroupEditorDialog
}
