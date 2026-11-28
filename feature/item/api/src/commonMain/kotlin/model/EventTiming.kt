package com.mkulikov.taskb.feature.item.api.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Event times are local civil times; changing the device's time zone does not move them. */
val Event.startDateTime: LocalDateTime?
    get() = dueDate?.let { date -> startTime?.let { time -> LocalDateTime(date = date, time = time) } }

/** The exclusive end of a timed event. */
val Event.endDateTime: LocalDateTime?
    get() {
        val start = startDateTime ?: return null
        val duration = durationMinutes ?: Event.DEFAULT_DURATION_MINUTES
        if (duration <= 0) return null
        return eventEndDateTime(date = start.date, time = start.time, durationMinutes = duration)
    }

/** Computes an event's end in local civil minutes without creating an event or reading the clock. */
fun eventEndDateTime(date: LocalDate, time: LocalTime, durationMinutes: Int): LocalDateTime {
    return com.mkulikov.taskb.core.utils.time.endDateTime(
        date = date,
        time = time,
        durationMinutes = durationMinutes,
    )
}

fun Event.occursOn(date: LocalDate): Boolean = overlapsDates(from = date, through = date)

/** An event ending at midnight does not occupy the day that starts at that midnight. */
fun Event.overlapsDates(from: LocalDate, through: LocalDate): Boolean {
    if (through < from) return false
    val start = dueDate ?: return false
    if (start > through) return false
    val end = endDateTime ?: return requireNotNull(value = endDate) >= from
    return end > LocalDateTime(date = from, time = LocalTime(hour = 0, minute = 0))
}
