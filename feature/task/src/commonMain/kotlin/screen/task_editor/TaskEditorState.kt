package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.task.model.Task
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.core.utils.time.currentDate
import kotlinx.datetime.LocalDate

internal data class TaskEditorState(
    val isNew: Boolean = true,
    val original: Task? = null,
    // Keep text, selection and IME composition synchronous, outside the Store's intent queue.
    val name: TextFieldState = TextFieldState(),
    val dueDate: LocalDate? = null,
    val groupId: Int? = null,
    val groups: List<Group> = emptyList(),
    val isLoadingGroups: Boolean = true,
    val groupsLoadError: String? = null,
    val isChoosingGroup: Boolean = false,
    val groupQuery: String = "",
    val today: LocalDate = currentDate(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    val loadError: String? = null,
    val dialog: TaskEditorDialog? = null,
) {
    val isBusy get() = isLoading || isLoadingGroups || isSaving
    val normalizedName get() = name.text.toString().trim()
    val canSave get() = !isBusy && !isFinished && !isChoosingGroup && loadError == null && groupsLoadError == null && dialog == null && normalizedName.isNotEmpty() &&
        (isNew || original?.let { it.name != normalizedName || it.dueDate != dueDate || it.groupId != groupId } == true)
}
internal sealed interface TaskEditorDialog {
    data object ConfirmDelete : TaskEditorDialog
    data object DatePicker : TaskEditorDialog
    data class Error(val message: String) : TaskEditorDialog
}
