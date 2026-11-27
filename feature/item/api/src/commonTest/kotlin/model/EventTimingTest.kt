package com.mkulikov.taskb.feature.item.api.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class EventTimingTest {
    @Test
    fun `long event ends on a later date and occupies every overlapping day`() {
        val event = Event(
            name = "Business trip",
            dueDate = LocalDate(year = 2026, month = 11, day = 6),
            startTime = LocalTime(hour = 18, minute = 0),
            durationMinutes = 3840,
        )
        assertEquals(
            expected = LocalDateTime(year = 2026, month = 11, day = 6, hour = 18, minute = 0),
            actual = event.startDateTime,
        )
        assertEquals(
            expected = LocalDateTime(year = 2026, month = 11, day = 9, hour = 10, minute = 0),
            actual = event.endDateTime,
        )
        for (day in 6..9) assertTrue(actual = event.occursOn(date = LocalDate(year = 2026, month = 11, day = day)))
        assertFalse(actual = event.occursOn(date = LocalDate(year = 2026, month = 11, day = 5)))
        assertFalse(actual = event.occursOn(date = LocalDate(year = 2026, month = 11, day = 10)))
        assertTrue(
            actual = event.overlapsDates(
                from = LocalDate(year = 2026, month = 11, day = 7),
                through = LocalDate(year = 2026, month = 11, day = 8),
            )
        )
    }

    @Test
    fun `end is exclusive across midnight month and year boundaries`() {
        val event = Event(
            name = "Flight",
            dueDate = LocalDate(year = 2026, month = 12, day = 31),
            startTime = LocalTime(hour = 23, minute = 30),
            durationMinutes = 30,
        )
        assertEquals(
            expected = LocalDateTime(year = 2027, month = 1, day = 1, hour = 0, minute = 0),
            actual = event.endDateTime,
        )
        assertTrue(actual = event.occursOn(date = LocalDate(year = 2026, month = 12, day = 31)))
        assertFalse(actual = event.occursOn(date = LocalDate(year = 2027, month = 1, day = 1)))
        assertTrue(
            actual = event.copy(durationMinutes = 31).occursOn(date = LocalDate(year = 2027, month = 1, day = 1))
        )
    }

    @Test
    fun `date-only events have no time or duration`() {
        val date = LocalDate(year = 2026, month = 11, day = 6)
        val event = Event(name = "Call", dueDate = date)
        assertNull(actual = event.startDateTime)
        assertNull(actual = event.endDateTime)
        assertTrue(actual = event.occursOn(date = date))
        assertFalse(actual = event.occursOn(date = LocalDate(year = 2026, month = 11, day = 7)))
        assertFalse(actual = event.copy(dueDate = null).occursOn(date = date))
    }

    @Test
    fun `a start time defaults to thirty minutes including legacy missing duration across midnight`() {
        val date = LocalDate(year = 2026, month = 11, day = 6)
        val event = Event(name = "Call", dueDate = date, startTime = LocalTime(hour = 12, minute = 0))
        assertEquals(expected = 30, actual = event.durationMinutes)
        assertEquals(
            expected = LocalDateTime(year = 2026, month = 11, day = 6, hour = 12, minute = 30),
            actual = event.endDateTime,
        )
        val legacy = event.copy(startTime = LocalTime(hour = 23, minute = 30), durationMinutes = null)
        assertEquals(
            expected = LocalDateTime(year = 2026, month = 11, day = 7, hour = 0, minute = 30),
            actual = legacy.endDateTime,
        )
        assertTrue(actual = legacy.occursOn(date = date))
        assertTrue(actual = legacy.occursOn(date = LocalDate(year = 2026, month = 11, day = 7)))
        assertFalse(actual = legacy.occursOn(date = LocalDate(year = 2026, month = 11, day = 8)))
    }

    @Test
    fun `all day ranges include both end dates and never acquire a time`() {
        val event = Event(name = "Vacation", dueDate = LocalDate(year = 2026, month = 11, day = 6), durationDays = 4)
        assertEquals(expected = LocalDate(year = 2026, month = 11, day = 9), actual = event.endDate)
        for (day in 6..9) assertTrue(actual = event.occursOn(date = LocalDate(year = 2026, month = 11, day = day)))
        assertFalse(actual = event.occursOn(date = LocalDate(year = 2026, month = 11, day = 5)))
        assertFalse(actual = event.occursOn(date = LocalDate(year = 2026, month = 11, day = 10)))
        assertNull(actual = event.startDateTime)
        assertNull(actual = event.endDateTime)
    }
}
