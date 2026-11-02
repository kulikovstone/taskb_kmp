package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.recurrence.nextRecurrenceDates
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.core.utils.time.currentDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import com.mkulikov.taskb.feature.task.api.model.taskEndDateTime

internal data class TaskEditorState(
    val isNew: Boolean = true,
    val original: Task? = null,
    // Keep text, selection and IME composition synchronous, outside the Store's intent queue.
    val name: TextFieldState = TextFieldState(),
    val description: TextFieldState = TextFieldState(),
    val dueDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = null,
    val customDuration: Boolean = false,
    val durationMode: TaskDurationMode = TaskDurationMode.Duration,
    val durationDays: String = "0",
    val durationHours: String = "1",
    val durationMinutePart: String = "0",
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
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
    val editedDurationMinutes: Int? get() {
        if (startTime == null || dueDate == null) return null
        if (!customDuration) return durationMinutes?.takeIf { it > 0 }
        val minutes = when (durationMode) {
            TaskDurationMode.Duration -> {
                val days = durationDays.toLongOrNull()?.takeIf { it >= 0 } ?: return null
                val hours = durationHours.toLongOrNull()?.takeIf { it in 0..23 } ?: return null
                val minute = durationMinutePart.toLongOrNull()?.takeIf { it in 0..59 } ?: return null
                days * 1440 + hours * 60 + minute
            }
            TaskDurationMode.EndDateTime -> {
                val date = endDate ?: return null
                val time = endTime ?: return null
                (date.toEpochDays() - dueDate.toEpochDays()) * 1440 +
                    time.hour * 60 + time.minute - startTime.hour * 60 - startTime.minute
            }
        }
        return minutes.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
    }
    val timingError: String? get() = when {
        startTime == null -> null
        dueDate == null -> "Choose a start date."
        editedDurationMinutes != null -> null
        !customDuration -> "Choose a positive duration."
        durationMode == TaskDurationMode.EndDateTime -> "The end date and time must be after the start."
        else -> "Enter a positive duration: days, hours (0–23) and minutes (0–59)."
    }
    val ending: LocalDateTime? get() = if (startTime == null || dueDate == null || editedDurationMinutes == null) null
        else taskEndDateTime(dueDate, startTime, requireNotNull(editedDurationMinutes))
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
        recurrenceError == null && timingError == null && (recurrence == null || upcomingDates.isNotEmpty()) &&
        (isNew || original?.let { it.name != normalizedName || it.description != description.text.toString() || it.dueDate != dueDate || it.startTime != startTime || it.durationMinutes != editedDurationMinutes || it.groupId != groupId || it.recurrence != editedRecurrence } == true)
}
internal enum class TaskDurationMode { Duration, EndDateTime }
internal sealed interface TaskEditorDialog {
    data object ConfirmDelete : TaskEditorDialog
    data object DatePicker : TaskEditorDialog
    data object RepeatEndDatePicker : TaskEditorDialog
    data object StartTimePicker : TaskEditorDialog
    data object EndTimePicker : TaskEditorDialog
    data object TaskEndDatePicker : TaskEditorDialog
    data object DeleteOccurrence : TaskEditorDialog
    data object MoveOccurrence : TaskEditorDialog
    data class Error(val message: String) : TaskEditorDialog
}
