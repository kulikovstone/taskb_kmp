package com.mkulikov.taskb.feature.task.screen.task_editor

import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlinx.datetime.LocalDate

internal sealed interface TaskEditorIntent {
    data class DateChanged(val date: LocalDate?) : TaskEditorIntent

    data class CalendarVisibilityChanged(val showInCalendar: Boolean) : TaskEditorIntent

    data class QuickDeleteChanged(val enabled: Boolean) : TaskEditorIntent

    data class GroupChanged(val id: Int?) : TaskEditorIntent

    data class RecurrenceChanged(val recurrence: TaskRecurrence?) : TaskEditorIntent

    data class RepeatEndDateChanged(val date: LocalDate) : TaskEditorIntent

    data object ChooseRepeatEndDate : TaskEditorIntent

    data class SaveOccurrence(val scope: TaskChangeScope) : TaskEditorIntent

    data class DeleteOccurrence(val scope: TaskChangeScope) : TaskEditorIntent

    data object ChooseDate : TaskEditorIntent

    data object RetryGroups : TaskEditorIntent

    data object Save : TaskEditorIntent

    data object PrepareDelete : TaskEditorIntent

    data object ConfirmDelete : TaskEditorIntent

    data object DismissDialog : TaskEditorIntent

    data object RetryLoad : TaskEditorIntent
}
