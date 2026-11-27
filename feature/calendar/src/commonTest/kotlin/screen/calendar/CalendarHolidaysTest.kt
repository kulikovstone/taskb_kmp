package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.Task
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth

class CalendarHolidaysTest {
    private val date = LocalDate(year = 2026, month = 11, day = 4)
    private val now = Instant.parse(input = "2026-11-06T10:00:00Z")
    private val holiday = PublicHoliday(id = "ru-unity", date = date, name = "Unity Day", countryCode = "RU")

    @Test
    fun `holidays precede events while overdue unfinished tasks remain first in past day cells`() {
        val task = Task(id = 1, name = "Overdue", dueDate = date, createdAt = now)
        val done = Task(id = 2, name = "Completed", dueDate = date, createdAt = now, completedAt = now)
        val event = Event(id = 1, name = "All day event", dueDate = date, createdAt = now)
        val timed = Event(
            id = 2,
            name = "Meeting",
            dueDate = date,
            startTime = LocalTime(hour = 12, minute = 0),
            createdAt = now,
        )
        val state = CalendarState(
            today = LocalDate(year = 2026, month = 11, day = 6),
            selectedDate = date,
            now = now,
            tasks = listOf(done, task),
            events = listOf(timed, event),
            holidayCalendar = HolidayCalendar(holidays = listOf(element = holiday)),
        )
        assertEquals(
            expected = listOf(
                CalendarItem.TaskItem(task = task),
                CalendarItem.HolidayItem(holiday = holiday),
                CalendarItem.EventItem(event = event),
                CalendarItem.EventItem(event = timed),
                CalendarItem.TaskItem(task = done),
            ),
            actual = state.monthItemsByDate[date],
        )
        for (today in listOf(date, LocalDate(year = 2026, month = 11, day = 1))) {
            assertEquals(
                expected = listOf(
                    CalendarItem.HolidayItem(holiday = holiday),
                    CalendarItem.EventItem(event = event),
                    CalendarItem.EventItem(event = timed),
                    CalendarItem.TaskItem(task = task),
                    CalendarItem.TaskItem(task = done),
                ),
                actual = state.copy(today = today).monthItemsByDate[date],
            )
        }
        assertEquals(
            expected = listOf(
                CalendarItem.HolidayItem(holiday = holiday),
                CalendarItem.EventItem(event = event),
                CalendarItem.EventItem(event = timed),
            ),
            actual = state.sidebarEventItems,
        )
        assertTrue(actual = state.copy(selectedDate = null).sidebarItems.none(predicate = CalendarItem::isHoliday))
    }

    @Test
    fun `holiday-only days do not qualify as completed days and duplicate source holidays appear once`() {
        val state = CalendarState(
            today = date,
            now = now,
            isLoading = false,
            holidayCalendar = HolidayCalendar(holidays = listOf(holiday, holiday)),
        )
        assertFalse(actual = state.isSelectedDayCompleted)
        assertEquals(
            expected = listOf(element = CalendarItem.HolidayItem(holiday = holiday)),
            actual = state.sidebarItems,
        )
        assertEquals(
            expected = listOf(element = CalendarItem.HolidayItem(holiday = holiday)),
            actual = state.monthItemsByDate[date],
        )
    }

    @Test
    fun `holiday result from an older displayed or selected month cannot overwrite current data`() {
        val month = YearMonth(year = 2026, month = 11)
        val state = CalendarState(today = date, month = month, now = now)
        val old = CalendarMessage.Holidays(
            month = YearMonth(year = 2026, month = 10),
            selectedMonth = null,
            value = HolidayCalendar(holidays = listOf(element = holiday)),
        )
        assertSame(expected = state, actual = reduceCalendar(state = state, message = old))
        val staleSelection = CalendarMessage.Holidays(
            month = month,
            selectedMonth = YearMonth(year = 2026, month = 12),
            value = HolidayCalendar(holidays = listOf(element = holiday)),
        )
        assertSame(expected = state, actual = reduceCalendar(state = state, message = staleSelection))
    }
}
