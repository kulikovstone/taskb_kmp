package com.mkulikov.taskb.feature.task.api.recurrence

import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency.*
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlinx.datetime.DayOfWeek.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class RecurrenceDatesTest {
    private fun date(value: String) = LocalDate.parse(value)
    private fun next(rule: TaskRecurrence, start: String) = nextRecurrenceDates(rule, date(start)).map { it.toString() }

    @Test fun `all seven requested patterns use calendar intervals`() {
        assertEquals(listOf("2026-11-02", "2026-11-03", "2026-11-04"), next(TaskRecurrence(Daily), "2026-11-02"))
        assertEquals(listOf("2026-11-20", "2026-12-20", "2027-01-20"), next(TaskRecurrence(Monthly, dayOfMonth = 20), "2026-11-20"))
        assertEquals(listOf("2026-11-13", "2027-05-13", "2027-11-13"), next(TaskRecurrence(Monthly, interval = 6, dayOfMonth = 13), "2026-11-13"))
        assertEquals(listOf("2027-08-26", "2028-08-26", "2029-08-26"), next(TaskRecurrence(Yearly, dayOfMonth = 26, month = 8), "2027-08-26"))
        assertEquals(listOf("2026-11-14", "2031-11-14", "2036-11-14"), next(TaskRecurrence(Yearly, interval = 5, dayOfMonth = 14, month = 11), "2026-11-14"))
        assertEquals(listOf("2026-11-03", "2026-11-05", "2026-11-10"), next(TaskRecurrence(Weekly, weekdays = setOf(TUESDAY, THURSDAY)), "2026-11-03"))
        assertEquals(listOf("2026-11-07", "2026-11-21", "2026-12-05"), next(TaskRecurrence(Weekly, interval = 2, weekdays = setOf(SATURDAY)), "2026-11-07"))
    }

    @Test fun `missing calendar days clamp without drifting later occurrences`() {
        assertEquals(listOf("2027-01-31", "2027-02-28", "2027-03-31"), next(TaskRecurrence(Monthly, dayOfMonth = 31), "2027-01-31"))
        assertEquals(listOf("2028-02-29", "2029-02-28", "2030-02-28"), next(TaskRecurrence(Yearly, month = 2, dayOfMonth = 29), "2028-02-29"))
        assertEquals(listOf("2027-02-28", "2027-03-31", "2027-04-30"), next(TaskRecurrence(Monthly, dayOfMonth = 31), "2027-02-28"))
    }

    @Test fun `end date is inclusive and count is global with a partial first week`() {
        val start = date("2026-11-05")
        val rule = TaskRecurrence(Weekly, weekdays = setOf(TUESDAY, THURSDAY), occurrenceCount = 3)
        assertEquals(listOf("2026-11-05", "2026-11-10", "2026-11-12"), next(rule, start.toString()))
        assertEquals(listOf(date("2026-11-12")), recurrenceDates(rule, start, date("2026-11-11"), date("2027-01-01")))
        assertEquals(2, recurrenceOrdinal(rule, start, date("2026-11-12")))
        assertNull(recurrenceOrdinal(rule, start, date("2026-11-17")))
        assertEquals(listOf("2026-11-05", "2026-11-10"), next(rule.copy(occurrenceCount = null, endDate = date("2026-11-10")), start.toString()))
    }

    @Test fun `late windows do not reset month phase or occurrence count`() {
        val rule = TaskRecurrence(Monthly, interval = 6, dayOfMonth = 13, occurrenceCount = 4)
        val start = date("2026-11-14")
        assertEquals(listOf(date("2027-11-13"), date("2028-05-13"), date("2028-11-13")), recurrenceDates(rule, start, date("2027-11-01"), date("2040-01-01")))
        assertEquals(1, recurrenceOrdinal(rule, start, date("2027-11-13")))
        assertEquals(3, recurrenceDates(TaskRecurrence(Daily), date("0001-01-01"), date("2026-11-01"), date("9999-01-01"), limit = 3).size)
    }
}
