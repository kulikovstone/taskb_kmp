package com.mkulikov.taskb.feature.item.screen.item_editor

import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.ItemType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

internal sealed interface ItemEditorIntent {
    sealed interface FormAction : ItemEditorIntent

    data class DateChanged(val date: LocalDate?) : ItemEditorIntent

    data class StartTimeChanged(val time: LocalTime?) : ItemEditorIntent

    data class DurationChanged(val minutes: Int) : FormAction

    data object ChooseStartTime : FormAction

    data object ChooseEventEndDate : FormAction

    data object ChooseEndTime : FormAction

    data class EventEndDateChanged(val date: LocalDate) : ItemEditorIntent

    data class EndTimeChanged(val time: LocalTime) : ItemEditorIntent

    data class GroupChanged(val id: Int?) : FormAction

    data class RecurrenceChanged(val recurrence: ItemRecurrence?) : FormAction

    data class RepeatEndDateChanged(val date: LocalDate) : ItemEditorIntent

    data object ChooseRepeatEndDate : FormAction

    data class SaveOccurrence(val scope: ItemChangeScope) : ItemEditorIntent

    data class DeleteOccurrence(val scope: ItemChangeScope) : ItemEditorIntent

    data object ChooseDate : FormAction

    data object RetryGroups : ItemEditorIntent

    data object Save : FormAction

    data class SelectType(val type: ItemType) : FormAction

    data class CalendarVisibilityChanged(val showInCalendar: Boolean) : FormAction

    data class QuickDeleteChanged(val enabled: Boolean) : FormAction

    data object PrepareDelete : FormAction

    data object ConfirmDelete : ItemEditorIntent

    data object DismissDialog : ItemEditorIntent

    data object RetryLoad : ItemEditorIntent
}
