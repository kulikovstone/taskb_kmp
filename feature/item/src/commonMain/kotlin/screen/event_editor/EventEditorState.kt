package com.mkulikov.taskb.feature.item.screen.event_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.screen.item_editor.IRecurrenceEditorState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

internal data class EventEditorState(
    val isNew: Boolean = true,
    val original: Event? = null,
    // Keep text, selection and IME composition synchronous, outside the Store's intent queue.
    val name: TextFieldState = TextFieldState(),
    val description: TextFieldState = TextFieldState(),
    val today: LocalDate = currentDate(),
    override val dueDate: LocalDate = today,
    val endDate: LocalDate = dueDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    override val recurrence: ItemRecurrence? = null,
    override val repeatInterval: TextFieldState = TextFieldState(initialText = "1"),
    override val repeatDay: TextFieldState = TextFieldState(initialText = "1"),
    override val repeatCount: TextFieldState = TextFieldState(initialText = "10"),
    val groupId: Int? = null,
    val groups: List<Group> = emptyList(),
    val isLoadingGroups: Boolean = true,
    val groupsLoadError: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    val loadError: String? = null,
    val dialog: EventEditorDialog? = null,
) : IRecurrenceEditorState {
    val isBusy
        get() = isLoading || isLoadingGroups || isSaving

    val normalizedName
        get() = name.text.toString().trim()

    val editedDurationDays: Int?
        get() = (endDate.toEpochDays() - dueDate.toEpochDays() + 1).takeIf { days ->
            days in 1..Int.MAX_VALUE.toLong()
        }?.toInt()

    val editedDurationMinutes: Int?
        get() {
            val start = startTime ?: return null
            val end = endTime ?: return null
            val minutes = (endDate.toEpochDays() - dueDate.toEpochDays()) * 1440 + end.hour * 60 + end.minute -
                start.hour * 60 -
                start.minute
            return minutes.takeIf { minutes -> minutes in 1..Int.MAX_VALUE.toLong() }?.toInt()
        }

    val savedDurationDays
        get() = if (startTime == null) editedDurationDays else 1

    val timingError: String?
        get() = when {
            endDate < dueDate -> "The end date must not be before the start date."
            startTime != null && editedDurationMinutes == null -> "The end date and time must be after the start."
            editedDurationDays == null -> "The date range is too long."
            else -> null
        }

    val canSave
        get() = !isBusy &&
            !isFinished &&
            loadError == null &&
            groupsLoadError == null &&
            dialog == null &&
            normalizedName.isNotEmpty() &&
            recurrenceError == null &&
            timingError == null &&
            (recurrence == null || upcomingDates.isNotEmpty()) &&
            (isNew ||
                original?.let { event ->
                    event.name != normalizedName ||
                        event.description != description.text.toString() ||
                        event.dueDate != dueDate ||
                        event.startTime != startTime ||
                        event.durationMinutes != editedDurationMinutes ||
                        event.durationDays != savedDurationDays ||
                        event.groupId != groupId ||
                        event.recurrence != editedRecurrence
                } == true)
}

internal sealed interface EventEditorDialog {
    data object ConfirmDelete : EventEditorDialog

    data object DatePicker : EventEditorDialog

    data object RepeatEndDatePicker : EventEditorDialog

    data object StartTimePicker : EventEditorDialog

    data object EndTimePicker : EventEditorDialog

    data object EventEndDatePicker : EventEditorDialog

    data object DeleteOccurrence : EventEditorDialog

    data object MoveOccurrence : EventEditorDialog

    data class Error(val message: String) : EventEditorDialog
}
