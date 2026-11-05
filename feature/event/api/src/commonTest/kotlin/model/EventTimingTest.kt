package com.mkulikov.taskb.feature.event.api.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventTimingTest {
    @Test fun `long event ends on a later date and occupies every overlapping day`() {
        val event = Event(name = "Business trip", dueDate = LocalDate(2026, 11, 6), startTime = LocalTime(18, 0), durationMinutes = 3840)
        assertEquals(LocalDateTime(2026, 11, 6, 18, 0), event.startDateTime)
        assertEquals(LocalDateTime(2026, 11, 9, 10, 0), event.endDateTime)
        for (day in 6..9) assertTrue(event.occursOn(LocalDate(2026, 11, day)))
        assertFalse(event.occursOn(LocalDate(2026, 11, 5)))
        assertFalse(event.occursOn(LocalDate(2026, 11, 10)))
        assertTrue(event.overlapsDates(LocalDate(2026, 11, 7), LocalDate(2026, 11, 8)))
    }

    @Test fun `end is exclusive across midnight month and year boundaries`() {
        val event = Event(name = "Flight", dueDate = LocalDate(2026, 12, 31), startTime = LocalTime(23, 30), durationMinutes = 30)
        assertEquals(LocalDateTime(2027, 1, 1, 0, 0), event.endDateTime)
        assertTrue(event.occursOn(LocalDate(2026, 12, 31)))
        assertFalse(event.occursOn(LocalDate(2027, 1, 1)))
        assertTrue(event.copy(durationMinutes = 31).occursOn(LocalDate(2027, 1, 1)))
    }

    @Test fun `date-only events have no time or duration`() {
        val date = LocalDate(2026, 11, 6)
        val event = Event(name = "Call", dueDate = date)
        assertNull(event.startDateTime)
        assertNull(event.endDateTime)
        assertTrue(event.occursOn(date))
        assertFalse(event.occursOn(LocalDate(2026, 11, 7)))
        assertFalse(event.copy(dueDate = null).occursOn(date))
    }

    @Test fun `a start time defaults to thirty minutes including legacy missing duration across midnight`() {
        val date = LocalDate(2026, 11, 6)
        val event = Event(name = "Call", dueDate = date, startTime = LocalTime(12, 0))
        assertEquals(30, event.durationMinutes)
        assertEquals(LocalDateTime(2026, 11, 6, 12, 30), event.endDateTime)
        val legacy = event.copy(startTime = LocalTime(23, 30), durationMinutes = null)
        assertEquals(LocalDateTime(2026, 11, 7, 0, 30), legacy.endDateTime)
        assertTrue(legacy.occursOn(date))
        assertTrue(legacy.occursOn(LocalDate(2026, 11, 7)))
        assertFalse(legacy.occursOn(LocalDate(2026, 11, 8)))
    }
    @Test fun `all day ranges include both end dates and never acquire a time`() {
        val event = Event(name = "Vacation", dueDate = LocalDate(2026, 11, 6), durationDays = 4)
        assertEquals(LocalDate(2026, 11, 9), event.endDate)
        for (day in 6..9) assertTrue(event.occursOn(LocalDate(2026, 11, day)))
        assertFalse(event.occursOn(LocalDate(2026, 11, 5)))
        assertFalse(event.occursOn(LocalDate(2026, 11, 10)))
        assertNull(event.startDateTime)
        assertNull(event.endDateTime)
    }
}
