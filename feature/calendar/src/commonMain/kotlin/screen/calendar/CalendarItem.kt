package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.startDateTime
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

internal sealed class CalendarItem {
    data class TaskItem(val task: Task) : CalendarItem()

    data class EventItem(val event: Event) : CalendarItem()

    data class HolidayItem(val holiday: PublicHoliday) : CalendarItem()

    val key
        get() = when (this) {
            is TaskItem -> task.key
            is EventItem -> "event:${event.key}"
            is HolidayItem -> "holiday:${holiday.id}"
        }

    val isEvent
        get() = this is EventItem

    val isHoliday
        get() = this is HolidayItem

    val id
        get() = when (this) {
            is TaskItem -> task.id
            is EventItem -> event.id
            is HolidayItem -> error(message = "Public holidays have no editable item ID")
        }

    val name
        get() = when (this) {
            is TaskItem -> task.name
            is EventItem -> event.name
            is HolidayItem -> holiday.name
        }

    val description
        get() = when (this) {
            is TaskItem -> task.description
            is EventItem -> event.description
            is HolidayItem -> ""
        }

    val dueDate
        get() = when (this) {
            is TaskItem -> task.dueDate
            is EventItem -> event.dueDate
            is HolidayItem -> holiday.date
        }

    val groupId
        get() = when (this) {
            is TaskItem -> task.groupId
            is EventItem -> event.groupId
            is HolidayItem -> null
        }

    val createdAt
        get() = when (this) {
            is TaskItem -> task.createdAt
            is EventItem -> event.createdAt
            is HolidayItem -> Instant.fromEpochMilliseconds(epochMilliseconds = 0)
        }

    val isCompleted
        get() = this is TaskItem && task.isCompleted

    val startDateTime
        get() = (this as? EventItem)?.event?.startDateTime

    val isDailyRoutine
        get() = this is TaskItem && task.isDailyRoutine

    val occurrenceId
        get() = (this as? TaskItem)?.task?.occurrenceId

    val isMultiDayCalendarEvent
        get() = this is EventItem && event.isMultiDayCalendarEvent

    fun moveTo(date: LocalDate): CalendarIntent {
        return when (this) {
            is TaskItem -> CalendarIntent.MoveTask(id = task.id, date = date, occurrenceId = task.occurrenceId)
            is EventItem -> CalendarIntent.MoveEvent(id = event.id, date = date, occurrenceId = event.occurrenceId)
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
