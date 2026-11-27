package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.Item
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.api.model.startDateTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

internal sealed class CalendarItem {
    sealed class Editable(val item: Item) : CalendarItem()

    data class TaskItem(val task: Task) : Editable(item = task)

    data class EventItem(val event: Event) : Editable(item = event)

    data class HolidayItem(val holiday: PublicHoliday) : CalendarItem()

    val key
        get() = when (this) {
            is Editable -> item.key
            is HolidayItem -> "holiday:${holiday.id}"
        }

    val isEvent
        get() = this is EventItem

    val isHoliday
        get() = this is HolidayItem

    val id
        get() = when (this) {
            is Editable -> item.id
            is HolidayItem -> error(message = "Public holidays have no editable item ID")
        }

    val name
        get() = when (this) {
            is Editable -> item.name
            is HolidayItem -> holiday.name
        }

    val description
        get() = when (this) {
            is Editable -> item.description
            is HolidayItem -> ""
        }

    val dueDate
        get() = when (this) {
            is Editable -> item.dueDate
            is HolidayItem -> holiday.date
        }

    val groupId
        get() = when (this) {
            is Editable -> item.groupId
            is HolidayItem -> null
        }

    val createdAt
        get() = when (this) {
            is Editable -> item.createdAt
            is HolidayItem -> Instant.fromEpochMilliseconds(epochMilliseconds = 0)
        }

    val isCompleted
        get() = this is TaskItem && task.isCompleted

    val startDateTime
        get() = (this as? EventItem)?.event?.startDateTime

    val isRecurring
        get() = this is TaskItem && task.isRecurring

    val occurrenceId
        get() = (this as? TaskItem)?.task?.occurrenceId

    val isMultiDayCalendarEvent
        get() = this is EventItem && event.isMultiDayCalendarEvent

    fun moveTo(date: LocalDate): CalendarIntent {
        return when (this) {
            is Editable -> CalendarIntent.MoveItem(
                id = item.id,
                type = item.type,
                date = date,
                occurrenceId = item.occurrenceId,
            )
            is HolidayItem -> error(message = "Public holidays cannot be moved")
        }
    }
}

internal fun calendarMonthItemText(item: CalendarItem, date: LocalDate?): String {
    return when (item) {
        is CalendarItem.TaskItem -> item.task.name
        is CalendarItem.EventItem -> calendarMonthEventText(task = item.event, date = date)
        is CalendarItem.HolidayItem -> item.holiday.name
    }
}
