package com.mkulikov.taskb.feature.item.screen.task_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.screen.item_editor.IRecurrenceEditorState
import kotlinx.datetime.LocalDate

internal data class TaskEditorState(
    val isNew: Boolean = true,
    val original: Task? = null,
    // Keep text, selection and IME composition synchronous, outside the Store's intent queue.
    val name: TextFieldState = TextFieldState(),
    val description: TextFieldState = TextFieldState(),
    override val dueDate: LocalDate? = null,
    val showInCalendar: Boolean = true,
    val quickDelete: Boolean = false,
    override val recurrence: ItemRecurrence? = null,
    override val repeatInterval: TextFieldState = TextFieldState(initialText = "1"),
    override val repeatDay: TextFieldState = TextFieldState(initialText = "1"),
    override val repeatCount: TextFieldState = TextFieldState(initialText = "10"),
    val groupId: Int? = null,
    val groups: List<Group> = emptyList(),
    val isLoadingGroups: Boolean = true,
    val groupsLoadError: String? = null,
    val today: LocalDate = currentDate(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    val loadError: String? = null,
    val dialog: TaskEditorDialog? = null,
) : IRecurrenceEditorState {
    val isBusy
        get() = isLoading || isLoadingGroups || isSaving

    val normalizedName
        get() = name.text.toString().trim()

    val canSave
        get() = !isBusy &&
            !isFinished &&
            loadError == null &&
            groupsLoadError == null &&
            dialog == null &&
            normalizedName.isNotEmpty() &&
            recurrenceError == null &&
            (recurrence == null || upcomingDates.isNotEmpty()) &&
            (isNew ||
                original?.let { task ->
                    task.name != normalizedName ||
                        task.description != description.text.toString() ||
                        task.dueDate != dueDate ||
                        task.showInCalendar != showInCalendar ||
                        task.quickDelete != quickDelete ||
                        task.groupId != groupId ||
                        task.recurrence != editedRecurrence
                } == true)
}

internal sealed interface TaskEditorDialog {
    data object ConfirmDelete : TaskEditorDialog

    data object DatePicker : TaskEditorDialog

    data object RepeatEndDatePicker : TaskEditorDialog

    data object DeleteOccurrence : TaskEditorDialog

    data object MoveOccurrence : TaskEditorDialog

    data class Error(val message: String) : TaskEditorDialog
}
