package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

class CalendarEventsTest {
    private val date = LocalDate(2026, 11, 6)
    private val now = Instant.parse("2026-11-06T12:00:00Z")

    @Test
    fun `only past day cells put unfinished tasks before events and completion restores event priority`() {
        val yesterday = date.minus(1, DateTimeUnit.DAY)
        val older = Task(1, "Older overdue task", yesterday, createdAt = now - 2.hours)
        val newer = Task(2, "Newer overdue task", yesterday, createdAt = now - 1.hours)
        val completed = Task(3, "Completed task", yesterday, createdAt = now, completedAt = now)
        val allDay = Event(1, "Vacation", yesterday, createdAt = now, durationDays = 3)
        val timed = Event(2, "Meeting", yesterday, createdAt = now, startTime = LocalTime(10, 0))
        val state = CalendarState(date, now = now, tasks = listOf(completed, older, newer), events = listOf(timed, allDay))
        val overdueFirst = listOf(CalendarItem.TaskItem(newer), CalendarItem.TaskItem(older),
            CalendarItem.EventItem(allDay), CalendarItem.EventItem(timed), CalendarItem.TaskItem(completed))
        assertEquals(overdueFirst, state.monthItemsByDate[yesterday])

        val eventsFirst = listOf(CalendarItem.EventItem(allDay), CalendarItem.EventItem(timed),
            CalendarItem.TaskItem(newer), CalendarItem.TaskItem(older), CalendarItem.TaskItem(completed))
        for (today in listOf(yesterday, yesterday.minus(1, DateTimeUnit.DAY))) {
            assertEquals(eventsFirst, state.copy(today = today).monthItemsByDate[yesterday])
        }
        val finished = state.copy(tasks = state.tasks.map { it.copy(completedAt = now) })
        assertEquals(listOf(CalendarItem.EventItem(allDay), CalendarItem.EventItem(timed)),
            finished.monthItemsByDate[yesterday]?.take(2))
        val reopened = finished.copy(tasks = finished.tasks.map { if (it.id == older.id) it.copy(completedAt = null) else it })
        assertEquals(CalendarItem.TaskItem(older), reopened.monthItemsByDate[yesterday]?.first())
    }

    @Test fun `Events contains all day events first then exact times while Tasks contains only ordinary active tasks`() {
        val main = Task(1, "Main task", date, createdAt = now)
        val daily = Task(2, "Daily task", date, recurrence = TaskRecurrence(RecurrenceFrequency.Daily))
        val completed = Task(3, "Completed task", date, completedAt = now)
        val oldAllDay = Event(1, "Vacation", LocalDate(2026, 11, 5), createdAt = Instant.parse("2026-11-01T10:00:00Z"), durationDays = 3)
        val newAllDay = Event(2, "Day off", date, createdAt = now)
        val morning = Event(3, "Morning", date, startTime = LocalTime(9, 0))
        val afternoon = Event(4, "Afternoon", date, startTime = LocalTime(15, 0))
        val state = CalendarState(date, now = now, tasks = listOf(completed, main, daily), events = listOf(
            afternoon, oldAllDay, morning, newAllDay,
            Event(5, "Other day", LocalDate(2026, 11, 10)), Event(6, "Legacy no date"),
        ))
        assertEquals(listOf(2, 1, 3, 4), state.sidebarEventItems.map { it.id })
        assertEquals(listOf(
            CalendarItem.EventItem(newAllDay), CalendarItem.EventItem(oldAllDay),
            CalendarItem.EventItem(morning), CalendarItem.EventItem(afternoon),
            CalendarItem.TaskItem(main), CalendarItem.TaskItem(completed),
        ).map { it.key }, state.monthItemsByDate[date]?.map { it.key })
        assertEquals(listOf(CalendarItem.TaskItem(main)), state.sidebarMainTaskItems)
        assertEquals(listOf(CalendarItem.TaskItem(daily)), state.sidebarDailyItems)
        assertEquals(listOf(CalendarItem.TaskItem(completed)), state.sidebarCompletedItems)
        val sections = state.sidebarEventItems + state.sidebarMainTaskItems + state.sidebarDailyItems + state.sidebarCompletedItems
        assertEquals(state.sidebarItems.map { it.key }.toSet(), sections.map { it.key }.toSet())
        assertEquals(sections.size, sections.distinctBy { it.key }.size)
        assertTrue(state.sidebarEventItems.all { it.isEvent })
    }

    @Test fun `legacy undated events stay in Events while undated tasks stay in Tasks`() {
        val task = Task(1, "No date task")
        val event = Event(1, "No date event")
        val state = CalendarState(date, selectedDate = null, tasks = listOf(task), events = listOf(event))
        assertEquals(listOf(CalendarItem.EventItem(event)), state.sidebarEventItems)
        assertEquals(listOf(CalendarItem.TaskItem(task)), state.sidebarMainTaskItems)
    }
}
