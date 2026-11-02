package com.mkulikov.taskb.feature.calendar.screen.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.Padding

internal val calendarMonthLabel = YearMonth.Format {
    monthName(MonthNames.ENGLISH_FULL)
    chars(" ")
    year()
}
internal val calendarDateLabel = LocalDate.Format {
    monthName(MonthNames.ENGLISH_FULL)
    chars(" ")
    day(Padding.NONE)
}
internal val calendarMonthAbbreviation = LocalDate.Format { monthName(MonthNames.ENGLISH_ABBREVIATED) }
internal val calendarSidebarDateLabel = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    day(Padding.NONE)
    chars(", ")
    year()
}
internal val calendarWeekdayLabel = LocalDate.Format { dayOfWeek(DayOfWeekNames.ENGLISH_FULL) }
