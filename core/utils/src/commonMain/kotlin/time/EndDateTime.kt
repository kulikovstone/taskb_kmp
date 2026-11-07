package com.mkulikov.taskb.core.utils.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** Adds a positive duration in local civil minutes. */
fun endDateTime(date: LocalDate, time: LocalTime, durationMinutes: Int): LocalDateTime {
    require(value = durationMinutes > 0)
    val minutes = time.hour * 60L + time.minute + durationMinutes
    return LocalDateTime(
        date = date.plus(value = minutes / 1440, unit = DateTimeUnit.DAY),
        time = LocalTime(hour = (minutes % 1440 / 60).toInt(), minute = (minutes % 60).toInt()),
    )
}
