package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.startDateTime
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.LocalDate

internal sealed class CalendarItem {
    data class TaskItem(val task: Task) : CalendarItem()
    data class EventItem(val event: Event) : CalendarItem()
    val key get() = when (this) { is TaskItem -> task.key; is EventItem -> "event:${event.key}" }
    val isEvent get() = this is EventItem
    val id get() = when (this) { is TaskItem -> task.id; is EventItem -> event.id }
    val name get() = when (this) { is TaskItem -> task.name; is EventItem -> event.name }
    val description get() = when (this) { is TaskItem -> task.description; is EventItem -> event.description }
    val dueDate get() = when (this) { is TaskItem -> task.dueDate; is EventItem -> event.dueDate }
    val groupId get() = when (this) { is TaskItem -> task.groupId; is EventItem -> event.groupId }
    val createdAt get() = when (this) { is TaskItem -> task.createdAt; is EventItem -> event.createdAt }
    val isCompleted get() = this is TaskItem && task.isCompleted
    val startDateTime get() = (this as? EventItem)?.event?.startDateTime
    val isDailyRoutine get() = this is TaskItem && task.isDailyRoutine
    val occurrenceId get() = (this as? TaskItem)?.task?.occurrenceId
    val isMultiDayCalendarEvent get() = this is EventItem && event.isMultiDayCalendarEvent
    fun moveTo(date: LocalDate): CalendarIntent = when (this) {
        is TaskItem -> CalendarIntent.MoveTask(task.id, date, task.occurrenceId)
        is EventItem -> CalendarIntent.MoveEvent(event.id, date, event.occurrenceId)
    }
}

internal fun calendarMonthItemText(item: CalendarItem, date: LocalDate?): String = when (item) {
    is CalendarItem.TaskItem -> item.task.name
    is CalendarItem.EventItem -> calendarMonthEventText(item.event, date)
}
