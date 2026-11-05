package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.endDateTime
import com.mkulikov.taskb.feature.event.api.model.startDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val eventDateLabel = LocalDate.Format {
    day(Padding.NONE)
    chars(" ")
    monthName(MonthNames.ENGLISH_ABBREVIATED)
}

internal fun LocalTime.calendarTimeLabel(): String =
    hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

internal val Event.calendarLastDate: LocalDate?
    get() {
        val end = endDateTime ?: return dueDate
        return if (end.time == LocalTime(0, 0)) end.date.minus(1, DateTimeUnit.DAY) else end.date
    }

internal val Event.isMultiDayCalendarEvent: Boolean
    get() = startDateTime != null && calendarLastDate?.let { it > requireNotNull(dueDate) } == true

internal fun Event.isPastCalendarEvent(now: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
    (endDateTime ?: startDateTime)?.let { it <= now.toLocalDateTime(timeZone) } == true

internal fun calendarMonthEventText(task: Event, date: LocalDate?): String {
    val start = task.startDateTime ?: return task.name
    if (!task.isMultiDayCalendarEvent || date == null || date == start.date) {
        return "${start.time.calendarTimeLabel()} ${task.name}"
    }
    if (date == task.calendarLastDate) {
        val end = requireNotNull(task.endDateTime)
        val time = if (end.time == LocalTime(0, 0)) "24:00" else end.time.calendarTimeLabel()
        return "${task.name} $time"
    }
    return task.name
}

internal fun calendarEventTimeRange(task: Event): String {
    val start = task.startDateTime ?: return ""
    val end = task.endDateTime
    val range = when {
        end == null -> start.time.calendarTimeLabel()
        start.date == end.date -> "${start.time.calendarTimeLabel()}–${end.time.calendarTimeLabel()}"
        else -> {
            fun dateLabel(date: LocalDate): String = eventDateLabel.format(date) +
                if (start.year != end.year) " ${date.year}" else ""
            "${dateLabel(start.date)}, ${start.time.calendarTimeLabel()}–${dateLabel(end.date)}, ${end.time.calendarTimeLabel()}"
        }
    }
    return "$range (${calendarEventDuration(task.durationMinutes ?: 60)})"
}

internal fun calendarEventDuration(minutes: Int): String {
    val days = minutes / 1440
    val hours = minutes % 1440 / 60
    val remainder = minutes % 60
    return buildList {
        if (days > 0) add("$days ${if (days == 1) "day" else "days"}")
        if (hours > 0) add(if (days == 0 && hours == 1 && remainder == 0) "1 hour" else "$hours hr")
        if (remainder > 0) add("$remainder min")
    }.joinToString(" ")
}
