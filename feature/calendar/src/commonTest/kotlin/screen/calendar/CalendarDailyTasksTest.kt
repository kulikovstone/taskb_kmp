package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `hidden daily tasks appear on the calendar only while overdue`() {
        val completed = daily(id = 1, date = LocalDate(year = 2026, month = 11, day = 4), completed = true)
            .copy(showInCalendar = false)
        val yesterday = LocalDate(year = 2026, month = 11, day = 5)
        val overdue = daily(id = 1, date = yesterday).copy(showInCalendar = false)
        val visibleCompleted = daily(id = 2, date = yesterday, completed = true)
        val hiddenToday = daily(id = 1).copy(showInCalendar = false)
        val today = daily(id = 2)
        val tomorrowDate = LocalDate(year = 2026, month = 11, day = 7)
        val hiddenTomorrow = daily(id = 1, date = tomorrowDate).copy(showInCalendar = false)
        val tomorrow = daily(id = 2, date = tomorrowDate)
        val event = Event(id = 4, name = "Meeting", dueDate = date, startTime = LocalTime(hour = 12, minute = 0))
        val bills = Task(id = 3, name = "Bills", dueDate = date, showInCalendar = false)
        val state = CalendarState(
            today = date,
            tasks = listOf(completed, overdue, visibleCompleted, today, hiddenToday, tomorrow, hiddenTomorrow, bills),
            events = listOf(element = event),
        )
        assertEquals(expected = listOf(today, hiddenToday, bills), actual = state.sidebarTasks)
        assertEquals(expected = listOf(today, hiddenToday), actual = state.sidebarRecurringTasks)
        assertEquals(
            expected = listOf(element = overdue),
            actual = state.copy(selectedDate = yesterday).sidebarRecurringTasks,
        )
        assertEquals(
            expected = listOf(element = CalendarItem.EventItem(event = event)),
            actual = state.sidebarEventItems,
        )
        assertTrue(actual = state.copy(selectedDate = completed.dueDate).sidebarRecurringTasks.isEmpty())
        assertEquals(
            expected = listOf(element = completed),
            actual = state.copy(selectedDate = completed.dueDate).sidebarCompletedTasks,
        )
        assertTrue(
            actual = state.copy(selectedDate = LocalDate(year = 2026, month = 11, day = 8))
                .sidebarRecurringTasks.isEmpty()
        )
        assertEquals(
            expected = mapOf<LocalDate?, List<Task>>(
                yesterday to listOf(overdue, visibleCompleted),
                date to listOf(element = today),
                tomorrowDate to listOf(element = tomorrow),
            ),
            actual = state.monthTasksByDate,
        )
    }

    @Test
    fun `all recurrence frequencies are grouped in the sidebar for the selected day`() {
        val routine = daily(id = 1).copy(showInCalendar = false)
        val alternate = Task(
            id = 2,
            name = "Every other day",
            dueDate = date,
            createdAt = created,
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
        val weeklyTask = alternate.copy(id = 6, name = "Weekly task", recurrence = weekly.recurrence)
        val monthlyTask = alternate.copy(
            id = 7,
            name = "Monthly task",
            recurrence = TaskRecurrence.monthly(date = date),
        )
        val yearlyTask = alternate.copy(
            id = 8,
            name = "Yearly task",
            recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Yearly, dayOfMonth = 6, month = 11),
        )
        val oneTime = Task(id = 9, name = "One-time task", dueDate = date, createdAt = created)
        val state = CalendarState(
            today = date,
            tasks = listOf(routine, alternate, weeklyTask, monthlyTask, yearlyTask, oneTime),
            events = listOf(weekly, meeting, dailyEvent),
        )
        assertEquals(
            expected = listOf(yearlyTask, monthlyTask, weeklyTask, alternate, routine),
            actual = state.sidebarRecurringTasks,
        )
        assertEquals(expected = listOf(5, 3, 4), actual = state.sidebarEventItems.map(transform = CalendarItem::id))
        assertEquals(expected = listOf(element = oneTime), actual = state.sidebarUntimedTasks)
        val sections = state.sidebarRecurringItems + state.sidebarEventItems + state.sidebarMainTaskItems
        assertEquals(
            expected = state.sidebarItems.map(transform = CalendarItem::key).toSet(),
            actual = sections.map(transform = CalendarItem::key).toSet(),
        )
        assertEquals(expected = sections.size, actual = sections.distinctBy(selector = CalendarItem::key).size)
        assertEquals(
            expected = listOf(5, 3, 4, 9, 8, 7, 6, 2),
            actual = state.monthItemsByDate[date]?.map(transform = CalendarItem::id),
        )
    }

    @Test
    fun `hidden routines stay out of month cells and their overflow counts`() {
        val routines = (1..20).map { taskId -> daily(id = taskId).copy(showInCalendar = false) }
        val undated = Task(id = 30, name = "No date", createdAt = created, showInCalendar = false)
        val state = CalendarState(today = date, tasks = routines + undated)
        assertTrue(actual = state.monthTasksByDate.isEmpty())
        assertEquals(expected = 20, actual = state.sidebarTasks.size)
        val noDate = state.copy(selectedDate = null)
        assertEquals(expected = listOf(element = undated), actual = noDate.sidebarTasks)
        assertTrue(actual = noDate.sidebarRecurringTasks.isEmpty())
        assertTrue(actual = noDate.sidebarEventItems.isEmpty())
        assertEquals(expected = listOf(element = undated), actual = noDate.sidebarUntimedTasks)
    }
}
