package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.recurrence.nextRecurrenceDates
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.core.utils.time.currentDate
import kotlinx.datetime.LocalDate

internal data class TaskEditorState(
    val isNew: Boolean = true,
    val original: Task? = null,
    // Keep text, selection and IME composition synchronous, outside the Store's intent queue.
    val name: TextFieldState = TextFieldState(),
    val description: TextFieldState = TextFieldState(),
    val dueDate: LocalDate? = null,
    val recurrence: TaskRecurrence? = null,
    val repeatInterval: String = "1",
    val repeatDay: String = "1",
    val repeatCount: String = "10",
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
) {
    val isBusy get() = isLoading || isLoadingGroups || isSaving
    val normalizedName get() = name.text.toString().trim()
    val recurrenceError: String? get() = recurrence?.let {
        when {
            repeatInterval.toIntOrNull()?.let { value -> value in 1..9999 } != true -> "Enter an interval between 1 and 9999."
            repeatDay.toIntOrNull()?.let { value -> value in 1..31 } != true -> "Enter a day between 1 and 31."
            it.occurrenceCount != null && repeatCount.toIntOrNull()?.let { value -> value in 1..1_000_000 } != true -> "Enter between 1 and 1000000 occurrences."
            dueDate == null -> "Choose a start date."
            it.endDate?.let { end -> end < dueDate } == true -> "The end date must not be before the start date."
            else -> null
        }
    }
    val editedRecurrence: TaskRecurrence? get() = recurrence?.copy(
        interval = repeatInterval.toIntOrNull()?.takeIf { it in 1..9999 } ?: recurrence.interval,
        dayOfMonth = repeatDay.toIntOrNull()?.takeIf { it in 1..31 } ?: recurrence.dayOfMonth,
        occurrenceCount = if (recurrence.occurrenceCount == null) null else repeatCount.toIntOrNull()?.takeIf { it in 1..1_000_000 } ?: recurrence.occurrenceCount,
    )
    val upcomingDates: List<LocalDate> get() = editedRecurrence?.let { rule -> dueDate?.let { nextRecurrenceDates(rule, it) } }.orEmpty()
    val canSave get() = !isBusy && !isFinished && loadError == null && groupsLoadError == null && dialog == null && normalizedName.isNotEmpty() &&
        recurrenceError == null && (recurrence == null || upcomingDates.isNotEmpty()) &&
        (isNew || original?.let { it.name != normalizedName || it.description != description.text.toString() || it.dueDate != dueDate || it.groupId != groupId || it.recurrence != editedRecurrence } == true)
}
internal sealed interface TaskEditorDialog {
    data object ConfirmDelete : TaskEditorDialog
    data object DatePicker : TaskEditorDialog
    data object RepeatEndDatePicker : TaskEditorDialog
    data object DeleteOccurrence : TaskEditorDialog
    data object MoveOccurrence : TaskEditorDialog
    data class Error(val message: String) : TaskEditorDialog
}
