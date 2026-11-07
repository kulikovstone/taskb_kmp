package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class CalendarDailyTasksTest {
    private val date = LocalDate(year = 2026, month = 11, day = 6)
    private val dailyRule = TaskRecurrence(frequency = RecurrenceFrequency.Daily)
    private val created = Instant.parse(input = "2026-11-01T07:00:00Z")

    private fun daily(id: Int, date: LocalDate = this.date, completed: Boolean = false): Task {
        return Task(
            id = id,
            name = "Daily $id",
            dueDate = date,
            createdAt = created,
            completedAt = created.takeIf { completed },
            recurrence = dailyRule,
            occurrenceId = TaskOccurrenceId(scheduleId = id.toLong(), scheduledDate = date),
        )
    }

    @Test
    fun `daily routines appear first only for their actual occurrence day and keep completed history`() {
        val previous = daily(id = 1, date = LocalDate(year = 2026, month = 11, day = 5), completed = true)
        val today = daily(id = 1)
        val tomorrow = daily(id = 1, date = LocalDate(year = 2026, month = 11, day = 7))
        val event = Event(id = 2, name = "Meeting", dueDate = date, startTime = LocalTime(hour = 12, minute = 0))
        val bills = Task(id = 3, name = "Bills", dueDate = date)
        val state = CalendarState(
            today = date,
            tasks = listOf(previous, today, tomorrow, bills),
            events = listOf(element = event),
        )
        assertEquals(expected = listOf(today, bills), actual = state.sidebarTasks)
        assertEquals(expected = listOf(element = today), actual = state.sidebarDailyTasks)
        assertEquals(
            expected = listOf(element = CalendarItem.EventItem(event = event)),
            actual = state.sidebarEventItems,
        )
        assertTrue(actual = state.copy(selectedDate = previous.dueDate).sidebarDailyTasks.isEmpty())
        assertEquals(
            expected = listOf(element = previous),
            actual = state.copy(selectedDate = previous.dueDate).sidebarCompletedTasks,
        )
        assertTrue(
            actual = state.copy(selectedDate = LocalDate(year = 2026, month = 11, day = 8)).sidebarDailyTasks.isEmpty()
        )
        assertTrue(actual = state.monthTasksByDate.values.flatten().none(predicate = Task::isDailyRoutine))
    }

    @Test
    fun `daily tasks stay in Daily while events including daily events stay in Event and the grid`() {
        val routine = daily(id = 1)
        val alternate = Task(
            id = 2,
            name = "Every other day",
            dueDate = date,
            recurrence = dailyRule.copy(interval = 2),
        )
        val weekly = Event(
            id = 3,
            name = "Weekly review",
            dueDate = date,
            recurrence = TaskRecurrence(
                frequency = RecurrenceFrequency.Weekly,
                weekdays = setOf(element = DayOfWeek.FRIDAY),
            ),
            startTime = LocalTime(hour = 10, minute = 0),
        )
        val meeting = Event(id = 4, name = "Meeting", dueDate = date, startTime = LocalTime(hour = 12, minute = 0))
        val dailyEvent = Event(
            id = 5,
            name = "Daily call",
            dueDate = date,
            recurrence = dailyRule,
            startTime = LocalTime(hour = 8, minute = 0),
        )
        val state = CalendarState(
            today = date,
            tasks = listOf(routine, alternate),
            events = listOf(weekly, meeting, dailyEvent),
        )
        assertEquals(expected = listOf(element = routine), actual = state.sidebarDailyTasks)
        assertEquals(expected = listOf(5, 3, 4), actual = state.sidebarEventItems.map(transform = CalendarItem::id))
        assertEquals(expected = listOf(element = alternate), actual = state.sidebarUntimedTasks)
        val sections = state.sidebarDailyItems + state.sidebarEventItems + state.sidebarMainTaskItems
        assertEquals(
            expected = state.sidebarItems.map(transform = CalendarItem::key).toSet(),
            actual = sections.map(transform = CalendarItem::key).toSet(),
        )
        assertEquals(expected = sections.size, actual = sections.distinctBy(selector = CalendarItem::key).size)
        assertEquals(expected = listOf(5, 3, 4, 2), actual = state.monthItemsByDate[date]?.map { item -> item.id })
        assertFalse(actual = alternate.isDailyRoutine)
    }

    @Test
    fun `routines never contribute to month cells or their overflow counts while no-date view is unchanged`() {
        val routines = (1..20).map { taskId -> daily(id = taskId) }
        val undated = Task(id = 30, name = "No date", createdAt = created)
        val state = CalendarState(today = date, tasks = routines + undated)
        assertTrue(actual = state.monthTasksByDate.isEmpty())
        assertEquals(expected = 20, actual = state.sidebarTasks.size)
        val noDate = state.copy(selectedDate = null)
        assertEquals(expected = listOf(element = undated), actual = noDate.sidebarTasks)
        assertTrue(actual = noDate.sidebarDailyTasks.isEmpty())
        assertTrue(actual = noDate.sidebarEventItems.isEmpty())
        assertEquals(expected = listOf(element = undated), actual = noDate.sidebarUntimedTasks)
    }
}
