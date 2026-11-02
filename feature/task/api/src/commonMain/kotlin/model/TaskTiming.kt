package com.mkulikov.taskb.feature.task.api.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** Task times are local civil times; changing the device's time zone does not move them. */
val Task.startDateTime: LocalDateTime?
    get() = dueDate?.let { date -> startTime?.let { LocalDateTime(date, it) } }

/** The exclusive end of an event. Legacy timed records without a duration use one hour. */
val Task.endDateTime: LocalDateTime?
    get() {
        val start = startDateTime ?: return null
        val duration = (durationMinutes ?: 60).takeIf { it > 0 } ?: return null
        return taskEndDateTime(start.date, start.time, duration)
    }

/** Computes a task's end in local civil minutes without creating a task or reading the clock. */
fun taskEndDateTime(date: LocalDate, time: LocalTime, durationMinutes: Int): LocalDateTime {
    require(durationMinutes > 0)
    val minutes = time.hour * 60L + time.minute + durationMinutes
    return LocalDateTime(
        date.plus(minutes / 1440, DateTimeUnit.DAY),
        LocalTime((minutes % 1440 / 60).toInt(), (minutes % 60).toInt()),
    )
}

fun Task.occursOn(date: LocalDate): Boolean = overlapsDates(date, date)

/** An event ending at midnight does not occupy the day that starts at that midnight. */
fun Task.overlapsDates(from: LocalDate, through: LocalDate): Boolean {
    if (through < from) return false
    val start = dueDate ?: return false
    if (start > through) return false
    val end = endDateTime ?: return start >= from
    return end > LocalDateTime(from, LocalTime(0, 0))
}
