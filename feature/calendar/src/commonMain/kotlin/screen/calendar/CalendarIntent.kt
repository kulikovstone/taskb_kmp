package com.mkulikov.taskb.feature.calendar.screen.calendar

import kotlinx.datetime.LocalDate
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope

internal sealed interface CalendarIntent {
    data object PreviousMonth : CalendarIntent
    data object NextMonth : CalendarIntent
    data object Today : CalendarIntent
    data object RetryLoad : CalendarIntent
    data object UndoMove : CalendarIntent
    data object DismissError : CalendarIntent
    data class MoveTask(val id: Int, val date: LocalDate, val occurrenceId: TaskOccurrenceId? = null) : CalendarIntent
    data class ToggleCompleted(val id: Int, val occurrenceId: TaskOccurrenceId? = null) : CalendarIntent
    data class ConfirmMove(val scope: TaskChangeScope) : CalendarIntent
    data object DismissMoveChoice : CalendarIntent
}
