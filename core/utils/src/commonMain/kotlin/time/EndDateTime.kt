package com.mkulikov.taskb.core.utils.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** Adds a positive duration in local civil minutes. */
fun endDateTime(date: LocalDate, time: LocalTime, durationMinutes: Int): LocalDateTime {
    require(durationMinutes > 0)
    val minutes = time.hour * 60L + time.minute + durationMinutes
    return LocalDateTime(
        date.plus(minutes / 1440, DateTimeUnit.DAY),
        LocalTime((minutes % 1440 / 60).toInt(), (minutes % 60).toInt()),
    )
}
