package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlin.test.*
import kotlin.time.Instant

class CalendarHolidaysTest {
    private val date = LocalDate(2026, 11, 4)
    private val now = Instant.parse("2026-11-06T10:00:00Z")
    private val holiday = PublicHoliday("ru-unity", date, "Unity Day", "RU")

    @Test
    fun `holidays precede events while overdue unfinished tasks remain first in past day cells`() {
        val task = Task(1, "Overdue", date, createdAt = now)
        val done = Task(2, "Completed", date, createdAt = now, completedAt = now)
        val event = Event(1, "All day event", date, createdAt = now)
        val timed = Event(2, "Meeting", date, startTime = LocalTime(12, 0), createdAt = now)
        val state = CalendarState(LocalDate(2026, 11, 6), selectedDate = date, now = now,
            tasks = listOf(done, task), events = listOf(timed, event),
            holidayCalendar = HolidayCalendar(holidays = listOf(holiday)))
        assertEquals(listOf(CalendarItem.TaskItem(task), CalendarItem.HolidayItem(holiday),
            CalendarItem.EventItem(event), CalendarItem.EventItem(timed), CalendarItem.TaskItem(done)), state.monthItemsByDate[date])
        for (today in listOf(date, LocalDate(2026, 11, 1))) {
            assertEquals(listOf(CalendarItem.HolidayItem(holiday), CalendarItem.EventItem(event),
                CalendarItem.EventItem(timed), CalendarItem.TaskItem(task), CalendarItem.TaskItem(done)),
                state.copy(today = today).monthItemsByDate[date])
        }
        assertEquals(listOf(CalendarItem.HolidayItem(holiday), CalendarItem.EventItem(event), CalendarItem.EventItem(timed)), state.sidebarEventItems)
        assertTrue(state.copy(selectedDate = null).sidebarItems.none { it.isHoliday })
    }

    @Test
    fun `holiday-only days do not qualify as completed days and duplicate source holidays appear once`() {
        val state = CalendarState(date, now = now, isLoading = false,
            holidayCalendar = HolidayCalendar(holidays = listOf(holiday, holiday)))
        assertFalse(state.isSelectedDayCompleted)
        assertEquals(listOf(CalendarItem.HolidayItem(holiday)), state.sidebarItems)
        assertEquals(listOf(CalendarItem.HolidayItem(holiday)), state.monthItemsByDate[date])
    }

    @Test
    fun `holiday result from an older displayed or selected month cannot overwrite current data`() {
        val month = YearMonth(2026, 11)
        val state = CalendarState(date, month = month, now = now)
        val old = CalendarMessage.Holidays(YearMonth(2026, 10), null, HolidayCalendar(holidays = listOf(holiday)))
        assertSame(state, reduceCalendar(state, old))
        val staleSelection = CalendarMessage.Holidays(month, YearMonth(2026, 12), HolidayCalendar(holidays = listOf(holiday)))
        assertSame(state, reduceCalendar(state, staleSelection))
    }
}
