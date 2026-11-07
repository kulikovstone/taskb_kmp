package com.mkulikov.taskb.feature.task.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class TaskDatesTest {
    @Test
    fun relativeLabelsWorkAcrossYearAndLeapDayBoundaries() {
        val newYear = LocalDate(year = 2027, month = 1, day = 1)
        assertEquals(
            expected = "Yesterday",
            actual = taskDateLabel(date = LocalDate(year = 2026, month = 12, day = 31), today = newYear),
        )
        assertEquals(
            expected = "Yesterday, Dec 31, 2026",
            actual = taskDateLabel(
                date = LocalDate(year = 2026, month = 12, day = 31),
                today = newYear,
                includeDate = true,
            ),
        )
        assertEquals(
            expected = "Today, Jan 1",
            actual = taskDateLabel(date = newYear, today = newYear, includeDate = true),
        )
        assertEquals(
            expected = "Tomorrow",
            actual = taskDateLabel(
                date = LocalDate(year = 2028, month = 2, day = 29),
                today = LocalDate(year = 2028, month = 2, day = 28),
            ),
        )
        assertEquals(
            expected = "Tomorrow, Mar 1",
            actual = taskDateLabel(
                date = LocalDate(year = 2028, month = 3, day = 1),
                today = LocalDate(year = 2028, month = 2, day = 29),
                includeDate = true,
            ),
        )
    }

    @Test
    fun absoluteLabelsRetainEnglishWeekdaysAndIncludeOtherYears() {
        val today = LocalDate(year = 2026, month = 10, day = 27)
        assertEquals(
            expected = "Fri, Oct 30",
            actual = taskDateLabel(date = LocalDate(year = 2026, month = 10, day = 30), today = today),
        )
        assertEquals(
            expected = "Oct 30",
            actual = taskDateLabel(
                date = LocalDate(year = 2026, month = 10, day = 30),
                today = today,
                includeDate = true,
            ),
        )
        assertEquals(
            expected = "Feb 29, 2028",
            actual = taskDateLabel(date = LocalDate(year = 2028, month = 2, day = 29), today = today),
        )
    }
}
