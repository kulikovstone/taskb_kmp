package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class CalendarEventTimeTest {
    private val start = LocalDate(year = 2026, month = 11, day = 6)
    private val trip = Event(
        id = 1,
        name = "Business trip",
        dueDate = start,
        startTime = LocalTime(hour = 18, minute = 0),
        durationMinutes = 3840,
    )

    @Test
    fun `multi-day tasks occupy every covered date and sort by their start before untimed tasks`() {
        val state = CalendarState(
            today = start,
            tasks = listOf(element = Task(id = 2, name = "Bills", dueDate = start)),
            events = listOf(
                trip,
                Event(
                    id = 3,
                    name = "Coffee break",
                    dueDate = start,
                    startTime = LocalTime(hour = 15, minute = 0),
                    durationMinutes = 30,
                ),
                Event(
                    id = 4,
                    name = "Team sync",
                    dueDate = start,
                    startTime = LocalTime(hour = 12, minute = 0),
                    durationMinutes = 60,
                ),
            ),
        )
        assertEquals(expected = listOf(4, 3, 1, 2), actual = state.sidebarItems.map(transform = CalendarItem::id))
        for (day in 7..9) assertEquals(
            expected = listOf(element = CalendarItem.EventItem(event = trip)),
            actual = state.monthItemsByDate[LocalDate(year = 2026, month = 11, day = day)],
        )
        assertTrue(actual = state.monthItemsByDate[LocalDate(year = 2026, month = 11, day = 10)].isNullOrEmpty())
        assertEquals(
            expected = listOf(element = CalendarItem.EventItem(event = trip)),
            actual = state.copy(selectedDate = LocalDate(year = 2026, month = 11, day = 8)).sidebarItems,
        )
    }

    @Test
    fun `events crossing a month boundary stay visible and expansion is bounded to visible dates`() {
        val long = trip.copy(dueDate = LocalDate(year = 2020, month = 1, day = 1), durationMinutes = Int.MAX_VALUE)
        val state = CalendarState(
            today = start,
            selectedDate = LocalDate(year = 2027, month = 2, day = 1),
            events = listOf(element = long),
        )
        assertTrue(
            actual = state.days.all { date ->
                state.monthItemsByDate[date] == listOf(element = CalendarItem.EventItem(event = long))
            }
        )
        assertEquals(expected = listOf(element = CalendarItem.EventItem(event = long)), actual = state.sidebarItems)
        assertEquals(expected = state.days.size, actual = state.monthItemsByDate.size)
    }

    @Test
    fun `month labels show start then intermediate title then symmetric end time`() {
        assertEquals(expected = "18:00 Business trip", actual = calendarMonthEventText(task = trip, date = start))
        assertEquals(
            expected = "Business trip",
            actual = calendarMonthEventText(task = trip, date = LocalDate(year = 2026, month = 11, day = 7)),
        )
        assertEquals(
            expected = "Business trip",
            actual = calendarMonthEventText(task = trip, date = LocalDate(year = 2026, month = 11, day = 8)),
        )
        assertEquals(
            expected = "Business trip 10:00",
            actual = calendarMonthEventText(task = trip, date = LocalDate(year = 2026, month = 11, day = 9)),
        )
        val midnight = trip.copy(startTime = LocalTime(hour = 18, minute = 0), durationMinutes = 1800)
        assertEquals(expected = LocalDate(year = 2026, month = 11, day = 7), actual = midnight.calendarLastDate)
        assertEquals(
            expected = "Business trip 24:00",
            actual = calendarMonthEventText(task = midnight, date = LocalDate(year = 2026, month = 11, day = 7)),
        )
    }

    @Test
    fun `time ranges include duration and dates only when needed`() {
        assertEquals(
            expected = "6 Nov, 18:00–9 Nov, 10:00 (2 days 16 hr)",
            actual = calendarEventTimeRange(task = trip),
        )
        assertEquals(
            expected = "12:00–13:00 (1 hour)",
            actual = calendarEventTimeRange(
                task = trip.copy(startTime = LocalTime(hour = 12, minute = 0), durationMinutes = 60)
            ),
        )
        assertEquals(
            expected = "15:00–15:30 (30 min)",
            actual = calendarEventTimeRange(
                task = trip.copy(startTime = LocalTime(hour = 15, minute = 0), durationMinutes = 30)
            ),
        )
        assertEquals(
            expected = "18:00–19:00 (1 hour)",
            actual = calendarEventTimeRange(task = trip.copy(durationMinutes = null)),
        )
        assertEquals(
            expected = "31 Dec 2026, 18:00–1 Jan 2027, 18:00 (1 day)",
            actual = calendarEventTimeRange(
                task = trip.copy(dueDate = LocalDate(year = 2026, month = 12, day = 31), durationMinutes = 1440)
            ),
        )
        assertEquals(expected = "2 hr 5 min", actual = calendarEventDuration(minutes = 125))
    }

    @Test
    fun `elapsed appearance uses local event end without completing an in-progress event`() {
        val meeting = trip.copy(startTime = LocalTime(hour = 12, minute = 0), durationMinutes = 60)
        assertFalse(
            actual = meeting.isPastCalendarEvent(
                now = Instant.parse(input = "2026-11-06T11:59:00Z"),
                timeZone = TimeZone.UTC,
            )
        )
        assertFalse(
            actual = meeting.isPastCalendarEvent(
                now = Instant.parse(input = "2026-11-06T12:30:00Z"),
                timeZone = TimeZone.UTC,
            )
        )
        assertTrue(
            actual = meeting.isPastCalendarEvent(
                now = Instant.parse(input = "2026-11-06T13:00:00Z"),
                timeZone = TimeZone.UTC,
            )
        )
        assertTrue(
            actual = meeting.isPastCalendarEvent(
                now = Instant.parse(input = "2026-11-06T08:00:00Z"),
                timeZone = TimeZone.of(zoneId = "Asia/Yekaterinburg"),
            )
        )
        assertFalse(
            actual = meeting
                .copy(durationMinutes = null)
                .isPastCalendarEvent(
                    now = Instant.parse(input = "2026-11-06T12:00:00Z"),
                    timeZone = TimeZone.UTC,
                )
        )
        assertTrue(
            actual = meeting
                .copy(durationMinutes = null)
                .isPastCalendarEvent(
                    now = Instant.parse(input = "2026-11-06T13:00:00Z"),
                    timeZone = TimeZone.UTC,
                )
        )
    }

    @Test
    fun `past multi-day entries use the displayed local date while the event stays active`() {
        val now = Instant.parse(input = "2026-11-07T12:00:00Z")
        for (event in listOf(trip, trip.copy(startTime = null, durationDays = 4))) {
            assertTrue(actual = event.isPastCalendarEntry(date = start, now = now, timeZone = TimeZone.UTC))
            assertFalse(
                actual = event.isPastCalendarEntry(
                    date = LocalDate(year = 2026, month = 11, day = 7),
                    now = now,
                    timeZone = TimeZone.UTC,
                )
            )
            assertFalse(
                actual = event.isPastCalendarEntry(
                    date = LocalDate(year = 2026, month = 11, day = 8),
                    now = now,
                    timeZone = TimeZone.UTC,
                )
            )
            assertFalse(actual = event.isPastCalendarEntry(date = null, now = now, timeZone = TimeZone.UTC))
            assertFalse(actual = event.isPastCalendarEvent(now = now, timeZone = TimeZone.UTC))
        }
        val localTomorrow = Instant.parse(input = "2026-11-06T21:00:00Z")
        assertFalse(actual = trip.isPastCalendarEntry(date = start, now = localTomorrow, timeZone = TimeZone.UTC))
        assertTrue(
            actual = trip.isPastCalendarEntry(
                date = start,
                now = localTomorrow,
                timeZone = TimeZone.of(zoneId = "Asia/Yekaterinburg"),
            )
        )
        assertTrue(
            actual = trip.isPastCalendarEntry(
                date = LocalDate(year = 2026, month = 11, day = 9),
                now = Instant.parse(input = "2026-11-09T10:00:00Z"),
                timeZone = TimeZone.UTC,
            )
        )
    }

    @Test
    fun `dragging an intermediate segment offsets the whole event and same-cell drops do nothing`() {
        val drag = CalendarDragState()
        val source = LocalDate(year = 2026, month = 11, day = 7)
        val target = LocalDate(year = 2026, month = 11, day = 12)
        drag.targets[source] = Rect(left = 0f, top = 0f, right = 100f, bottom = 100f)
        drag.targets[target] = Rect(left = 100f, top = 0f, right = 200f, bottom = 100f)
        val moves = mutableListOf<Pair<Int, LocalDate>>()
        drag.begin(
            task = CalendarItem.EventItem(event = trip),
            position = Offset(x = 50f, y = 50f),
            size = IntSize(width = 100, height = 25),
            dense = true,
            sourceDate = source,
        )
        drag.finish { id, date -> moves += id to date }
        assertTrue(actual = moves.isEmpty())
        drag.begin(
            task = CalendarItem.EventItem(event = trip),
            position = Offset(x = 50f, y = 50f),
            size = IntSize(width = 100, height = 25),
            dense = true,
            sourceDate = source,
        )
        drag.update(position = Offset(x = 150f, y = 50f))
        drag.finish { id, date -> moves += id to date }
        assertEquals(expected = listOf(element = 1 to LocalDate(year = 2026, month = 11, day = 11)), actual = moves)
    }

    @Test
    fun `all day first last intermediate and single day cells show the title without a clock`() {
        val event = Event(name = "Vacation", dueDate = start, durationDays = 4)
        for (day in 6..9) assertEquals(
            expected = "Vacation",
            actual = calendarMonthEventText(task = event, date = LocalDate(year = 2026, month = 11, day = day)),
        )
        assertEquals(expected = LocalDate(year = 2026, month = 11, day = 9), actual = event.calendarLastDate)
        assertEquals(
            expected = "Vacation",
            actual = calendarMonthEventText(task = event.copy(durationDays = 1), date = start),
        )
    }
}
