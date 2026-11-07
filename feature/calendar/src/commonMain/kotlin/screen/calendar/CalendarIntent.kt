package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import kotlinx.datetime.LocalDate

internal sealed interface CalendarIntent {
    data object RetryHolidays : CalendarIntent

    data object RetryWeather : CalendarIntent

    data object PreviousMonth : CalendarIntent

    data object NextMonth : CalendarIntent

    data object Today : CalendarIntent

    data object ChooseDate : CalendarIntent

    data class DateChosen(val date: LocalDate) : CalendarIntent

    data object DismissDatePicker : CalendarIntent

    data class SelectDay(val date: LocalDate) : CalendarIntent

    data object ShowUnscheduled : CalendarIntent

    data object RetryLoad : CalendarIntent

    data object UndoMove : CalendarIntent

    data object DismissError : CalendarIntent

    data class MoveEvent(
        val id: Int,
        val date: LocalDate,
        val occurrenceId: EventOccurrenceId? = null,
    ) : CalendarIntent

    data class MoveTask(
        val id: Int,
        val date: LocalDate,
        val occurrenceId: TaskOccurrenceId? = null,
    ) : CalendarIntent

    data class ToggleCompleted(val id: Int, val occurrenceId: TaskOccurrenceId? = null) : CalendarIntent

    data class ConfirmMove(val scope: TaskChangeScope) : CalendarIntent

    data object DismissMoveChoice : CalendarIntent
}
