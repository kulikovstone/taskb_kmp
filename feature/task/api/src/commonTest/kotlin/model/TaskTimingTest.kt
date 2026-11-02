package com.mkulikov.taskb.feature.task.api.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskTimingTest {
    @Test fun `long event ends on a later date and occupies every overlapping day`() {
        val task = Task(name = "Business trip", dueDate = LocalDate(2026, 11, 6), startTime = LocalTime(18, 0), durationMinutes = 3840)
        assertEquals(LocalDateTime(2026, 11, 6, 18, 0), task.startDateTime)
        assertEquals(LocalDateTime(2026, 11, 9, 10, 0), task.endDateTime)
        for (day in 6..9) assertTrue(task.occursOn(LocalDate(2026, 11, day)))
        assertFalse(task.occursOn(LocalDate(2026, 11, 5)))
        assertFalse(task.occursOn(LocalDate(2026, 11, 10)))
        assertTrue(task.overlapsDates(LocalDate(2026, 11, 7), LocalDate(2026, 11, 8)))
    }

    @Test fun `end is exclusive across midnight month and year boundaries`() {
        val task = Task(name = "Flight", dueDate = LocalDate(2026, 12, 31), startTime = LocalTime(23, 30), durationMinutes = 30)
        assertEquals(LocalDateTime(2027, 1, 1, 0, 0), task.endDateTime)
        assertTrue(task.occursOn(LocalDate(2026, 12, 31)))
        assertFalse(task.occursOn(LocalDate(2027, 1, 1)))
        assertTrue(task.copy(durationMinutes = 31).occursOn(LocalDate(2027, 1, 1)))
    }

    @Test fun `date-only tasks have no time or duration`() {
        val date = LocalDate(2026, 11, 6)
        val task = Task(name = "Call", dueDate = date)
        assertNull(task.startDateTime)
        assertNull(task.endDateTime)
        assertTrue(task.occursOn(date))
        assertFalse(task.occursOn(LocalDate(2026, 11, 7)))
        assertFalse(task.copy(dueDate = null).occursOn(date))
    }

    @Test fun `a start time defaults to one hour including legacy missing duration across midnight`() {
        val date = LocalDate(2026, 11, 6)
        val task = Task(name = "Call", dueDate = date, startTime = LocalTime(12, 0))
        assertEquals(60, task.durationMinutes)
        assertEquals(LocalDateTime(2026, 11, 6, 13, 0), task.endDateTime)
        val legacy = task.copy(startTime = LocalTime(23, 30), durationMinutes = null)
        assertEquals(LocalDateTime(2026, 11, 7, 0, 30), legacy.endDateTime)
        assertTrue(legacy.occursOn(date))
        assertTrue(legacy.occursOn(LocalDate(2026, 11, 7)))
        assertFalse(legacy.occursOn(LocalDate(2026, 11, 8)))
    }
}
