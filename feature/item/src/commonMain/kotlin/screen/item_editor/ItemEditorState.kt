package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.Item
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.feature.item.api.model.Task
import kotlinx.datetime.LocalDate

internal data class ItemEditorState(
    val isNew: Boolean = true,
    val original: Item? = null,
    val name: TextFieldState = TextFieldState(),
    val description: TextFieldState = TextFieldState(),
    val schedule: ScheduleDraft = ScheduleDraft(details = TaskSchedule()),
    val inactiveSchedules: Map<ItemType, ScheduleDraft> = emptyMap(),
    val groupId: Int? = null,
    val groups: List<Group> = emptyList(),
    val isLoadingGroups: Boolean = true,
    val groupsLoadError: String? = null,
    val today: LocalDate = currentDate(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    val loadError: String? = null,
    val dialog: ItemEditorDialog? = null,
) {
    val type: ItemType
        get() = schedule.details.type

    val normalizedName: String
        get() = name.text.toString().trim()

    val isBusy: Boolean
        get() = isLoading || isLoadingGroups || isSaving

    val enabled: Boolean
        get() = !isBusy && !isFinished && dialog == null && loadError == null && groupsLoadError == null

    val canSave: Boolean
        get() = enabled &&
            normalizedName.isNotEmpty() &&
            schedule.recurrenceError == null &&
            (schedule.details as? EventSchedule)?.timingError == null &&
            (schedule.recurrence == null || schedule.upcomingDates.isNotEmpty()) &&
            (isNew || original != null && editedItem() != original)

    fun editedItem(): Item = when (val details = schedule.details) {
        is TaskSchedule -> ((original as? Task) ?: Task(name = "")).copy(
            name = normalizedName,
            description = description.text.toString(),
            dueDate = details.dueDate,
            showInCalendar = details.showInCalendar,
            quickDelete = details.quickDelete,
            groupId = groupId,
            recurrence = schedule.editedRecurrence,
        )
        is EventSchedule -> ((original as? Event) ?: Event(name = "")).copy(
            name = normalizedName,
            description = description.text.toString(),
            dueDate = details.dueDate,
            groupId = groupId,
            recurrence = schedule.editedRecurrence,
            startTime = details.startTime,
            durationMinutes = details.editedDurationMinutes,
            durationDays = requireNotNull(value = details.savedDurationDays),
        )
    }

    val needsMoveScope: Boolean
        get() {
            val item = original ?: return false
            if (item.occurrenceId == null || item.recurrence != schedule.editedRecurrence) return false
            if (item.dueDate != schedule.dueDate) return true
            val details = schedule.details
            return item is Event && details is EventSchedule &&
                (item.startTime != details.startTime ||
                    item.durationMinutes != details.editedDurationMinutes ||
                    item.durationDays != details.savedDurationDays)
        }
}

internal sealed interface ItemEditorDialog {
    data object ConfirmDelete : ItemEditorDialog

    data object DatePicker : ItemEditorDialog

    data object RepeatEndDatePicker : ItemEditorDialog

    data object StartTimePicker : ItemEditorDialog

    data object EndTimePicker : ItemEditorDialog

    data object EventEndDatePicker : ItemEditorDialog

    data object DeleteOccurrence : ItemEditorDialog

    data object MoveOccurrence : ItemEditorDialog

    data class Error(val message: String) : ItemEditorDialog
}
