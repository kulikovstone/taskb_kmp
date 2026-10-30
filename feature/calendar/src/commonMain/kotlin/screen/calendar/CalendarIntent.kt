package com.mkulikov.taskb.feature.calendar.screen.calendar

import kotlinx.datetime.LocalDate

internal sealed interface CalendarIntent {
    data object PreviousMonth : CalendarIntent
    data object NextMonth : CalendarIntent
    data object Today : CalendarIntent
    data object RetryLoad : CalendarIntent
    data object UndoMove : CalendarIntent
    data object DismissError : CalendarIntent
    data class MoveTask(val id: Int, val date: LocalDate) : CalendarIntent
    data class ToggleCompleted(val id: Int) : CalendarIntent
}
