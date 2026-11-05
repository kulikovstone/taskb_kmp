package com.mkulikov.taskb.feature.event.screen.event_editor

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope

internal sealed interface EventEditorIntent {
    data class DateChanged(val date: LocalDate) : EventEditorIntent
    data class StartTimeChanged(val time: LocalTime?) : EventEditorIntent
    data class DurationChanged(val minutes: Int) : EventEditorIntent
    data object ChooseStartTime : EventEditorIntent
    data object ChooseEventEndDate : EventEditorIntent
    data object ChooseEndTime : EventEditorIntent
    data class EventEndDateChanged(val date: LocalDate) : EventEditorIntent
    data class EndTimeChanged(val time: LocalTime) : EventEditorIntent
    data class GroupChanged(val id: Int?) : EventEditorIntent
    data class RecurrenceChanged(val recurrence: EventRecurrence?) : EventEditorIntent
    data class RepeatIntervalChanged(val value: String) : EventEditorIntent
    data class RepeatDayChanged(val value: String) : EventEditorIntent
    data class RepeatCountChanged(val value: String) : EventEditorIntent
    data class RepeatEndDateChanged(val date: LocalDate) : EventEditorIntent
    data object ChooseRepeatEndDate : EventEditorIntent
    data class SaveOccurrence(val scope: EventChangeScope) : EventEditorIntent
    data class DeleteOccurrence(val scope: EventChangeScope) : EventEditorIntent
    data object ChooseDate : EventEditorIntent
    data object RetryGroups : EventEditorIntent
    data object Save : EventEditorIntent
    data object PrepareDelete : EventEditorIntent
    data object ConfirmDelete : EventEditorIntent
    data object DismissDialog : EventEditorIntent
    data object RetryLoad : EventEditorIntent
}
