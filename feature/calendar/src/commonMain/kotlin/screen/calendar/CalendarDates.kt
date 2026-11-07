package com.mkulikov.taskb.feature.calendar.screen.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding

internal val calendarMonthLabel = YearMonth.Format {
    monthName(names = MonthNames.ENGLISH_FULL)
    chars(value = " ")
    year()
}
internal val calendarDateLabel = LocalDate.Format {
    monthName(names = MonthNames.ENGLISH_FULL)
    chars(value = " ")
    day(padding = Padding.NONE)
}
internal val calendarMonthAbbreviation = LocalDate.Format { monthName(names = MonthNames.ENGLISH_ABBREVIATED) }
internal val calendarSidebarDateLabel = LocalDate.Format {
    monthName(names = MonthNames.ENGLISH_ABBREVIATED)
    chars(value = " ")
    day(padding = Padding.NONE)
    chars(value = ", ")
    year()
}
internal val calendarWeekdayLabel = LocalDate.Format { dayOfWeek(names = DayOfWeekNames.ENGLISH_FULL) }
