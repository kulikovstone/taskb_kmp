package com.mkulikov.taskb.feature.event.api.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Event times are local civil times; changing the device's time zone does not move them. */
val Event.startDateTime: LocalDateTime?
    get() = dueDate?.let { date -> startTime?.let { LocalDateTime(date, it) } }

/** The exclusive end of an event. Legacy timed records without a duration use one hour. */
val Event.endDateTime: LocalDateTime?
    get() {
        val start = startDateTime ?: return null
        val duration = (durationMinutes ?: 60).takeIf { it > 0 } ?: return null
        return eventEndDateTime(start.date, start.time, duration)
    }

/** Computes an event's end in local civil minutes without creating an event or reading the clock. */
fun eventEndDateTime(date: LocalDate, time: LocalTime, durationMinutes: Int): LocalDateTime {
    return com.mkulikov.taskb.core.utils.time.endDateTime(date, time, durationMinutes)
}

fun Event.occursOn(date: LocalDate): Boolean = overlapsDates(date, date)

/** An event ending at midnight does not occupy the day that starts at that midnight. */
fun Event.overlapsDates(from: LocalDate, through: LocalDate): Boolean {
    if (through < from) return false
    val start = dueDate ?: return false
    if (start > through) return false
    val end = endDateTime ?: return start >= from
    return end > LocalDateTime(from, LocalTime(0, 0))
}
