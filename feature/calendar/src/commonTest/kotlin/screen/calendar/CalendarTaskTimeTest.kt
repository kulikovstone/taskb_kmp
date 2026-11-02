package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CalendarTaskTimeTest {
    private val start = LocalDate(2026, 11, 6)
    private val trip = Task(1, "Business trip", start, startTime = LocalTime(18, 0), durationMinutes = 3840)

    @Test
    fun `multi-day tasks occupy every covered date and sort by their start before untimed tasks`() {
        val state = CalendarState(start, tasks = listOf(
            Task(2, "Bills", start), trip,
            Task(3, "Coffee break", start, startTime = LocalTime(15, 0), durationMinutes = 30),
            Task(4, "Team sync", start, startTime = LocalTime(12, 0), durationMinutes = 60),
        ))
        assertEquals(listOf(4, 3, 1, 2), state.sidebarTasks.map { it.id })
        for (day in 7..9) assertEquals(listOf(trip), state.tasksByDate[LocalDate(2026, 11, day)])
        assertFalse(state.tasksByDate.containsKey(LocalDate(2026, 11, 10)))
        assertEquals(listOf(trip), state.copy(selectedDate = LocalDate(2026, 11, 8)).sidebarTasks)
    }

    @Test
    fun `events crossing a month boundary stay visible and expansion is bounded to visible dates`() {
        val long = trip.copy(dueDate = LocalDate(2020, 1, 1), durationMinutes = Int.MAX_VALUE)
        val state = CalendarState(start, selectedDate = LocalDate(2027, 2, 1), tasks = listOf(long))
        assertTrue(state.days.all { state.tasksByDate[it] == listOf(long) })
        assertEquals(listOf(long), state.sidebarTasks)
        assertEquals(state.days.size + 1, state.tasksByDate.size)
    }

    @Test
    fun `month labels show start then intermediate title then symmetric end time`() {
        assertEquals("18:00 Business trip", calendarMonthTaskText(trip, start))
        assertEquals("Business trip", calendarMonthTaskText(trip, LocalDate(2026, 11, 7)))
        assertEquals("Business trip", calendarMonthTaskText(trip, LocalDate(2026, 11, 8)))
        assertEquals("Business trip 10:00", calendarMonthTaskText(trip, LocalDate(2026, 11, 9)))
        val midnight = trip.copy(startTime = LocalTime(18, 0), durationMinutes = 1800)
        assertEquals(LocalDate(2026, 11, 7), midnight.calendarLastDate)
        assertEquals("Business trip 24:00", calendarMonthTaskText(midnight, LocalDate(2026, 11, 7)))
    }

    @Test
    fun `time ranges include duration and dates only when needed`() {
        assertEquals("6 Nov, 18:00–9 Nov, 10:00 (2 days 16 hr)", calendarTaskTimeRange(trip))
        assertEquals("12:00–13:00 (1 hour)", calendarTaskTimeRange(trip.copy(startTime = LocalTime(12, 0), durationMinutes = 60)))
        assertEquals("15:00–15:30 (30 min)", calendarTaskTimeRange(trip.copy(startTime = LocalTime(15, 0), durationMinutes = 30)))
        assertEquals("18:00–19:00 (1 hour)", calendarTaskTimeRange(trip.copy(durationMinutes = null)))
        assertEquals("31 Dec 2026, 18:00–1 Jan 2027, 18:00 (1 day)",
            calendarTaskTimeRange(trip.copy(dueDate = LocalDate(2026, 12, 31), durationMinutes = 1440)))
        assertEquals("2 hr 5 min", calendarTaskDuration(125))
    }

    @Test
    fun `elapsed appearance uses local event end without completing an in-progress event`() {
        val meeting = trip.copy(startTime = LocalTime(12, 0), durationMinutes = 60)
        assertFalse(meeting.isPastCalendarEvent(Instant.parse("2026-11-06T11:59:00Z"), TimeZone.UTC))
        assertFalse(meeting.isPastCalendarEvent(Instant.parse("2026-11-06T12:30:00Z"), TimeZone.UTC))
        assertTrue(meeting.isPastCalendarEvent(Instant.parse("2026-11-06T13:00:00Z"), TimeZone.UTC))
        assertTrue(meeting.isPastCalendarEvent(Instant.parse("2026-11-06T08:00:00Z"), TimeZone.of("Asia/Yekaterinburg")))
        assertFalse(meeting.copy(durationMinutes = null).isPastCalendarEvent(Instant.parse("2026-11-06T12:00:00Z"), TimeZone.UTC))
        assertTrue(meeting.copy(durationMinutes = null).isPastCalendarEvent(Instant.parse("2026-11-06T13:00:00Z"), TimeZone.UTC))
        assertFalse(meeting.isCompleted)
    }

    @Test
    fun `dragging an intermediate segment offsets the whole event and same-cell drops do nothing`() {
        val drag = CalendarDragState()
        val source = LocalDate(2026, 11, 7)
        val target = LocalDate(2026, 11, 12)
        drag.targets[source] = Rect(0f, 0f, 100f, 100f)
        drag.targets[target] = Rect(100f, 0f, 200f, 100f)
        val moves = mutableListOf<Pair<Int, LocalDate>>()
        drag.begin(trip, Offset(50f, 50f), IntSize(100, 25), true, source)
        drag.finish { id, date -> moves += id to date }
        assertTrue(moves.isEmpty())
        drag.begin(trip, Offset(50f, 50f), IntSize(100, 25), true, source)
        drag.update(Offset(150f, 50f))
        drag.finish { id, date -> moves += id to date }
        assertEquals(listOf(1 to LocalDate(2026, 11, 11)), moves)
    }
}
