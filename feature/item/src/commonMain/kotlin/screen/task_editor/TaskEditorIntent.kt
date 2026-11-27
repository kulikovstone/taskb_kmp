package com.mkulikov.taskb.feature.item.screen.task_editor

import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import kotlinx.datetime.LocalDate

internal sealed interface TaskEditorIntent {
    data class DateChanged(val date: LocalDate?) : TaskEditorIntent

    data class CalendarVisibilityChanged(val showInCalendar: Boolean) : TaskEditorIntent

    data class QuickDeleteChanged(val enabled: Boolean) : TaskEditorIntent

    data class GroupChanged(val id: Int?) : TaskEditorIntent

    data class RecurrenceChanged(val recurrence: ItemRecurrence?) : TaskEditorIntent

    data class RepeatEndDateChanged(val date: LocalDate) : TaskEditorIntent

    data object ChooseRepeatEndDate : TaskEditorIntent

    data class SaveOccurrence(val scope: ItemChangeScope) : TaskEditorIntent

    data class DeleteOccurrence(val scope: ItemChangeScope) : TaskEditorIntent

    data object ChooseDate : TaskEditorIntent

    data object RetryGroups : TaskEditorIntent

    data object Save : TaskEditorIntent

    data object PrepareDelete : TaskEditorIntent

    data object ConfirmDelete : TaskEditorIntent

    data object DismissDialog : TaskEditorIntent

    data object RetryLoad : TaskEditorIntent
}
