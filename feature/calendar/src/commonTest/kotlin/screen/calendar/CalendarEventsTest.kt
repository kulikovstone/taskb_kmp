package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus

class CalendarEventsTest {
    private val date = LocalDate(year = 2026, month = 11, day = 6)
    private val now = Instant.parse(input = "2026-11-06T12:00:00Z")

    @Test
    fun `only past day cells put unfinished tasks before events and completion restores event priority`() {
        val yesterday = date.minus(value = 1, unit = DateTimeUnit.DAY)
        val older = Task(id = 1, name = "Older overdue task", dueDate = yesterday, createdAt = now - 2.hours)
        val newer = Task(id = 2, name = "Newer overdue task", dueDate = yesterday, createdAt = now - 1.hours)
        val completed = Task(id = 3, name = "Completed task", dueDate = yesterday, createdAt = now, completedAt = now)
        val allDay = Event(id = 1, name = "Vacation", dueDate = yesterday, createdAt = now, durationDays = 3)
        val timed = Event(
            id = 2,
            name = "Meeting",
            dueDate = yesterday,
            createdAt = now,
            startTime = LocalTime(hour = 10, minute = 0),
        )
        val state = CalendarState(
            today = date,
            now = now,
            tasks = listOf(completed, older, newer),
            events = listOf(timed, allDay),
        )
        val overdueFirst = listOf(
            CalendarItem.TaskItem(task = newer),
            CalendarItem.TaskItem(task = older),
            CalendarItem.EventItem(event = allDay),
            CalendarItem.EventItem(event = timed),
            CalendarItem.TaskItem(task = completed),
        )
        assertEquals(expected = overdueFirst, actual = state.monthItemsByDate[yesterday])

        val eventsFirst = listOf(
            CalendarItem.EventItem(event = allDay),
            CalendarItem.EventItem(event = timed),
            CalendarItem.TaskItem(task = newer),
            CalendarItem.TaskItem(task = older),
            CalendarItem.TaskItem(task = completed),
        )
        for (today in listOf(yesterday, yesterday.minus(value = 1, unit = DateTimeUnit.DAY))) {
            assertEquals(expected = eventsFirst, actual = state.copy(today = today).monthItemsByDate[yesterday])
        }
        val finished = state.copy(tasks = state.tasks.map { task -> task.copy(completedAt = now) })
        assertEquals(
            expected = listOf(CalendarItem.EventItem(event = allDay), CalendarItem.EventItem(event = timed)),
            actual = finished.monthItemsByDate[yesterday]?.take(n = 2),
        )
        val reopened = finished.copy(
            tasks = finished.tasks.map { task -> if (task.id == older.id) task.copy(completedAt = null) else task },
        )
        assertEquals(
            expected = CalendarItem.TaskItem(task = older),
            actual = reopened.monthItemsByDate[yesterday]?.first(),
        )
    }

    @Test
    fun `Events contains all day events first then exact times while Tasks contains only ordinary active tasks`() {
        val main = Task(id = 1, name = "Main task", dueDate = date, createdAt = now)
        val daily = Task(
            id = 2,
            name = "Daily task",
            dueDate = date,
            recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily),
            showInCalendar = false,
        )
        val completed = Task(id = 3, name = "Completed task", dueDate = date, completedAt = now)
        val oldAllDay = Event(
            id = 1,
            name = "Vacation",
            dueDate = LocalDate(year = 2026, month = 11, day = 5),
            createdAt = Instant.parse(input = "2026-11-01T10:00:00Z"),
            durationDays = 3,
        )
        val newAllDay = Event(id = 2, name = "Day off", dueDate = date, createdAt = now)
        val morning = Event(id = 3, name = "Morning", dueDate = date, startTime = LocalTime(hour = 9, minute = 0))
        val afternoon = Event(id = 4, name = "Afternoon", dueDate = date, startTime = LocalTime(hour = 15, minute = 0))
        val state = CalendarState(
            today = date,
            now = now,
            tasks = listOf(completed, main, daily),
            events = listOf(
                afternoon,
                oldAllDay,
                morning,
                newAllDay,
                Event(id = 5, name = "Other day", dueDate = LocalDate(year = 2026, month = 11, day = 10)),
                Event(id = 6, name = "Legacy no date"),
            ),
        )
        assertEquals(expected = listOf(2, 1, 3, 4), actual = state.sidebarEventItems.map(transform = CalendarItem::id))
        assertEquals(
            expected = listOf(
                    CalendarItem.EventItem(event = newAllDay),
                    CalendarItem.EventItem(event = oldAllDay),
                    CalendarItem.EventItem(event = morning),
                    CalendarItem.EventItem(event = afternoon),
                    CalendarItem.TaskItem(task = main),
                    CalendarItem.TaskItem(task = completed),
                )
                .map(transform = CalendarItem::key),
            actual = state.monthItemsByDate[date]?.map(transform = CalendarItem::key),
        )
        assertEquals(
            expected = listOf(element = CalendarItem.TaskItem(task = main)),
            actual = state.sidebarMainTaskItems,
        )
        assertEquals(
            expected = listOf(element = CalendarItem.TaskItem(task = daily)),
            actual = state.sidebarRecurringItems,
        )
        assertEquals(
            expected = listOf(element = CalendarItem.TaskItem(task = completed)),
            actual = state.sidebarCompletedItems,
        )
        val sections = state.sidebarEventItems +
            state.sidebarMainTaskItems +
            state.sidebarRecurringItems +
            state.sidebarCompletedItems
        assertEquals(
            expected = state.sidebarItems.map(transform = CalendarItem::key).toSet(),
            actual = sections.map(transform = CalendarItem::key).toSet(),
        )
        assertEquals(expected = sections.size, actual = sections.distinctBy(selector = CalendarItem::key).size)
        assertTrue(actual = state.sidebarEventItems.all(predicate = CalendarItem::isEvent))
    }

    @Test
    fun `legacy undated events stay in Events while undated tasks stay in Tasks`() {
        val task = Task(id = 1, name = "No date task")
        val event = Event(id = 1, name = "No date event")
        val state = CalendarState(
            today = date,
            selectedDate = null,
            tasks = listOf(element = task),
            events = listOf(element = event),
        )
        assertEquals(
            expected = listOf(element = CalendarItem.EventItem(event = event)),
            actual = state.sidebarEventItems,
        )
        assertEquals(
            expected = listOf(element = CalendarItem.TaskItem(task = task)),
            actual = state.sidebarMainTaskItems,
        )
    }
}
