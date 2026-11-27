package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.endDateTime
import com.mkulikov.taskb.feature.item.api.model.startDateTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private val eventDateLabel = LocalDate.Format {
    day(padding = Padding.NONE)
    chars(value = " ")
    monthName(names = MonthNames.ENGLISH_ABBREVIATED)
}

internal fun LocalTime.calendarTimeLabel(): String {
    return hour.toString().padStart(length = 2, padChar = '0') +
        ":" +
        minute.toString().padStart(length = 2, padChar = '0')
}

internal val Event.calendarLastDate: LocalDate?
    get() {
        val end = endDateTime ?: return endDate
        return if (end.time == LocalTime(hour = 0, minute = 0)) {
            end.date.minus(value = 1, unit = DateTimeUnit.DAY)
        } else {
            end.date
        }
    }

internal val Event.isMultiDayCalendarEvent: Boolean
    get() = calendarLastDate?.let { date -> date > requireNotNull(value = dueDate) } == true

internal fun Event.isPastCalendarEvent(
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean {
    return (endDateTime ?: startDateTime)?.let { dateTime ->
        dateTime <= now.toLocalDateTime(timeZone = timeZone)
    } == true
}

internal fun Event.isPastCalendarEntry(
    date: LocalDate?,
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean {
    return date?.let { date -> date < now.toLocalDateTime(timeZone = timeZone).date } == true ||
        isPastCalendarEvent(now = now, timeZone = timeZone)
}

internal fun calendarMonthEventText(task: Event, date: LocalDate?, endTimeFirst: Boolean = false): String {
    val start = task.startDateTime ?: return task.name
    if (!task.isMultiDayCalendarEvent || date == null || date == start.date) {
        return "${start.time.calendarTimeLabel()} ${task.name}"
    }
    if (date == task.calendarLastDate) {
        val end = requireNotNull(value = task.endDateTime)
        val time = if (end.time == LocalTime(hour = 0, minute = 0)) "24:00" else end.time.calendarTimeLabel()
        return if (endTimeFirst) "$time ${task.name}" else "${task.name} $time"
    }
    return task.name
}

internal fun calendarEventTimeRange(
    task: Event,
    timeColor: Color = Color.Unspecified,
    onFilterByDate: ((LocalDate) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    val start = task.startDateTime ?: return@buildAnnotatedString
    val end = task.endDateTime
    val showDates = end != null && start.date != end.date

    fun appendDate(date: LocalDate) {
        val from = length
        append(text = eventDateLabel.format(value = date))
        if (end != null && start.year != end.year) append(text = " ${date.year}")
        if (onFilterByDate != null) {
            addLink(
                clickable = LinkAnnotation.Clickable(tag = date.toString()) { _ -> onFilterByDate(date) },
                start = from,
                end = length,
            )
        }
        append(text = ", ")
    }

    val clockStyle = SpanStyle(color = timeColor)
    if (showDates) appendDate(date = start.date)
    withStyle(style = clockStyle) { append(text = start.time.calendarTimeLabel()) }
    if (end != null) {
        append(text = "–")
        if (showDates) appendDate(date = end.date)
        withStyle(style = clockStyle) { append(text = end.time.calendarTimeLabel()) }
    }
    append(text = " (${calendarEventDuration(minutes = task.durationMinutes ?: 60)})")
}

internal fun calendarEventDuration(minutes: Int): String {
    val days = minutes / 1440
    val hours = minutes % 1440 / 60
    val remainder = minutes % 60
    return buildList {
            if (days > 0) add(element = "$days ${if (days == 1) "day" else "days"}")
            if (hours > 0) add(element = if (days == 0 && hours == 1 && remainder == 0) "1 hour" else "$hours hr")
            if (remainder > 0) add(element = "$remainder min")
        }
        .joinToString(separator = " ")
}
