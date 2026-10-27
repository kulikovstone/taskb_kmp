package com.mkulikov.taskb.feature.task.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class TaskDatesTest {
    @Test
    fun relativeLabelsWorkAcrossYearAndLeapDayBoundaries() {
        val newYear = LocalDate(2027, 1, 1)
        assertEquals("Yesterday", taskDateLabel(LocalDate(2026, 12, 31), newYear))
        assertEquals("Yesterday, Dec 31, 2026", taskDateLabel(LocalDate(2026, 12, 31), newYear, true))
        assertEquals("Today, Jan 1", taskDateLabel(newYear, newYear, true))
        assertEquals("Tomorrow", taskDateLabel(LocalDate(2028, 2, 29), LocalDate(2028, 2, 28)))
        assertEquals("Tomorrow, Mar 1", taskDateLabel(LocalDate(2028, 3, 1), LocalDate(2028, 2, 29), true))
    }

    @Test
    fun absoluteLabelsRetainEnglishWeekdaysAndIncludeOtherYears() {
        val today = LocalDate(2026, 10, 27)
        assertEquals("Fri, Oct 30", taskDateLabel(LocalDate(2026, 10, 30), today))
        assertEquals("Oct 30", taskDateLabel(LocalDate(2026, 10, 30), today, true))
        assertEquals("Feb 29, 2028", taskDateLabel(LocalDate(2028, 2, 29), today))
    }
}
