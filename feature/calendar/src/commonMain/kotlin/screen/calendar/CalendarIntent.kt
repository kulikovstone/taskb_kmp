package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.ItemType
import kotlinx.datetime.LocalDate

internal sealed interface CalendarIntent {
    data object RetryTaskList : CalendarIntent

    data object RetryHolidays : CalendarIntent

    data object RetryWeather : CalendarIntent

    data object PreviousMonth : CalendarIntent

    data object NextMonth : CalendarIntent

    data object PreviousYear : CalendarIntent

    data object NextYear : CalendarIntent

    data object Today : CalendarIntent

    data class SelectDay(val date: LocalDate) : CalendarIntent

    data object ShowUnscheduled : CalendarIntent

    data object RetryLoad : CalendarIntent

    data object UndoMove : CalendarIntent

    data object DismissError : CalendarIntent

    data class MoveItem(
        val id: Int,
        val type: ItemType,
        val date: LocalDate,
        val occurrenceId: ItemOccurrenceId? = null,
    ) : CalendarIntent

    data class ToggleCompleted(val id: Int, val occurrenceId: ItemOccurrenceId? = null) : CalendarIntent

    data class PrepareQuickDelete(val id: Int, val occurrenceId: ItemOccurrenceId) : CalendarIntent

    data object ConfirmQuickDelete : CalendarIntent

    data object DismissQuickDelete : CalendarIntent

    data class ConfirmMove(val scope: ItemChangeScope) : CalendarIntent

    data object DismissMoveChoice : CalendarIntent
}
