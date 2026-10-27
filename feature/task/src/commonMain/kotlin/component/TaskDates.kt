package com.mkulikov.taskb.feature.task.component

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val shortDate = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    day(Padding.NONE)
}
private val dateWithYear = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    day(Padding.NONE)
    chars(", ")
    year()
}
private val dateWithWeekday = LocalDate.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(", ")
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    day(Padding.NONE)
}

internal fun taskDateLabel(date: LocalDate, today: LocalDate, includeDate: Boolean = false): String {
    val relative = when (date) {
        today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        today -> "Today"
        today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
        else -> null
    }
    val formatted = (if (date.year != today.year) dateWithYear else shortDate).format(date)
    return if (relative != null) if (includeDate) "$relative, $formatted" else relative
    else if (includeDate || date.year != today.year) formatted
    else dateWithWeekday.format(date)
}
