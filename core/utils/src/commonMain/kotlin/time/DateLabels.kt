package com.mkulikov.taskb.core.utils.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val shortDate = LocalDate.Format {
    monthName(names = MonthNames.ENGLISH_ABBREVIATED)
    chars(value = " ")
    day(padding = Padding.NONE)
}
private val dateWithYear = LocalDate.Format {
    monthName(names = MonthNames.ENGLISH_ABBREVIATED)
    chars(value = " ")
    day(padding = Padding.NONE)
    chars(value = ", ")
    year()
}
private val dateWithWeekday = LocalDate.Format {
    dayOfWeek(names = DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(value = ", ")
    monthName(names = MonthNames.ENGLISH_ABBREVIATED)
    chars(value = " ")
    day(padding = Padding.NONE)
}

fun dateLabel(date: LocalDate, today: LocalDate, includeDate: Boolean = false): String {
    val relative = when (date) {
        today.minus(value = 1, unit = DateTimeUnit.DAY) -> "Yesterday"
        today -> "Today"
        today.plus(value = 1, unit = DateTimeUnit.DAY) -> "Tomorrow"
        else -> null
    }
    val formatted = (if (date.year != today.year) dateWithYear else shortDate).format(value = date)
    return if (relative != null) {
        if (includeDate) "$relative, $formatted" else relative
    } else if (includeDate || date.year != today.year) {
        formatted
    } else {
        dateWithWeekday.format(value = date)
    }
}
