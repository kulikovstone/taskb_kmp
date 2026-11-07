package com.mkulikov.taskb.feature.task.api.recurrence

import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency.*
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlin.test.*
import kotlinx.datetime.DayOfWeek.*
import kotlinx.datetime.LocalDate

class RecurrenceDatesTest {
    private fun date(value: String) = LocalDate.parse(input = value)

    private fun next(rule: TaskRecurrence, start: String): List<String> {
        return nextRecurrenceDates(
            rule = rule,
            start = date(value = start),
        ).map(transform = LocalDate::toString)
    }

    @Test
    fun `all seven requested patterns use calendar intervals`() {
        assertEquals(
            expected = listOf("2026-11-02", "2026-11-03", "2026-11-04"),
            actual = next(rule = TaskRecurrence(frequency = Daily), start = "2026-11-02"),
        )
        assertEquals(
            expected = listOf("2026-11-20", "2026-12-20", "2027-01-20"),
            actual = next(rule = TaskRecurrence(frequency = Monthly, dayOfMonth = 20), start = "2026-11-20"),
        )
        assertEquals(
            expected = listOf("2026-11-13", "2027-05-13", "2027-11-13"),
            actual = next(
                rule = TaskRecurrence(frequency = Monthly, interval = 6, dayOfMonth = 13),
                start = "2026-11-13",
            ),
        )
        assertEquals(
            expected = listOf("2027-08-26", "2028-08-26", "2029-08-26"),
            actual = next(rule = TaskRecurrence(frequency = Yearly, dayOfMonth = 26, month = 8), start = "2027-08-26"),
        )
        assertEquals(
            expected = listOf("2026-11-14", "2031-11-14", "2036-11-14"),
            actual = next(
                rule = TaskRecurrence(frequency = Yearly, interval = 5, dayOfMonth = 14, month = 11),
                start = "2026-11-14",
            ),
        )
        assertEquals(
            expected = listOf("2026-11-03", "2026-11-05", "2026-11-10"),
            actual = next(
                rule = TaskRecurrence(frequency = Weekly, weekdays = setOf(TUESDAY, THURSDAY)),
                start = "2026-11-03",
            ),
        )
        assertEquals(
            expected = listOf("2026-11-07", "2026-11-21", "2026-12-05"),
            actual = next(
                rule = TaskRecurrence(frequency = Weekly, interval = 2, weekdays = setOf(element = SATURDAY)),
                start = "2026-11-07",
            ),
        )
    }

    @Test
    fun `missing calendar days clamp without drifting later occurrences`() {
        assertEquals(
            expected = listOf("2027-01-31", "2027-02-28", "2027-03-31"),
            actual = next(rule = TaskRecurrence(frequency = Monthly, dayOfMonth = 31), start = "2027-01-31"),
        )
        assertEquals(
            expected = listOf("2028-02-29", "2029-02-28", "2030-02-28"),
            actual = next(rule = TaskRecurrence(frequency = Yearly, month = 2, dayOfMonth = 29), start = "2028-02-29"),
        )
        assertEquals(
            expected = listOf("2027-02-28", "2027-03-31", "2027-04-30"),
            actual = next(rule = TaskRecurrence(frequency = Monthly, dayOfMonth = 31), start = "2027-02-28"),
        )
    }

    @Test
    fun `end date is inclusive and count is global with a partial first week`() {
        val start = date(value = "2026-11-05")
        val rule = TaskRecurrence(frequency = Weekly, weekdays = setOf(TUESDAY, THURSDAY), occurrenceCount = 3)
        assertEquals(
            expected = listOf("2026-11-05", "2026-11-10", "2026-11-12"),
            actual = next(rule = rule, start = start.toString()),
        )
        assertEquals(
            expected = listOf(element = date(value = "2026-11-12")),
            actual = recurrenceDates(
                rule = rule,
                start = start,
                from = date(value = "2026-11-11"),
                through = date(value = "2027-01-01"),
            ),
        )
        assertEquals(
            expected = 2,
            actual = recurrenceOrdinal(rule = rule, start = start, date = date(value = "2026-11-12")),
        )
        assertNull(actual = recurrenceOrdinal(rule = rule, start = start, date = date(value = "2026-11-17")))
        assertEquals(
            expected = listOf("2026-11-05", "2026-11-10"),
            actual = next(
                rule = rule.copy(occurrenceCount = null, endDate = date(value = "2026-11-10")),
                start = start.toString(),
            ),
        )
    }

    @Test
    fun `late windows do not reset month phase or occurrence count`() {
        val rule = TaskRecurrence(frequency = Monthly, interval = 6, dayOfMonth = 13, occurrenceCount = 4)
        val start = date(value = "2026-11-14")
        assertEquals(
            expected = listOf(date(value = "2027-11-13"), date(value = "2028-05-13"), date(value = "2028-11-13")),
            actual = recurrenceDates(
                rule = rule,
                start = start,
                from = date(value = "2027-11-01"),
                through = date(value = "2040-01-01"),
            ),
        )
        assertEquals(
            expected = 1,
            actual = recurrenceOrdinal(rule = rule, start = start, date = date(value = "2027-11-13")),
        )
        assertEquals(
            expected = 3,
            actual = recurrenceDates(
                    rule = TaskRecurrence(frequency = Daily),
                    start = date(value = "0001-01-01"),
                    from = date(value = "2026-11-01"),
                    through = date(value = "9999-01-01"),
                    limit = 3,
                )
                .size,
        )
    }
}
