package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CalendarDailyTasksTest {
    private val date = LocalDate(2026, 11, 6)
    private val dailyRule = TaskRecurrence(frequency = RecurrenceFrequency.Daily)
    private val created = Instant.parse("2026-11-01T07:00:00Z")

    private fun daily(id: Int, date: LocalDate = this.date, completed: Boolean = false) =
        Task(id, "Daily $id", date, createdAt = created, completedAt = created.takeIf { completed }, recurrence = dailyRule,
            occurrenceId = TaskOccurrenceId(id.toLong(), date))

    @Test
    fun `daily routines appear first only for their actual occurrence day and keep completed history`() {
        val previous = daily(1, LocalDate(2026, 11, 5), completed = true)
        val today = daily(1)
        val tomorrow = daily(1, LocalDate(2026, 11, 7))
        val event = Event(2, "Meeting", date, startTime = LocalTime(12, 0))
        val bills = Task(3, "Bills", date)
        val state = CalendarState(date, tasks = listOf(previous, today, tomorrow, bills), events = listOf(event))
        assertEquals(listOf(today, bills), state.sidebarTasks)
        assertEquals(listOf(today), state.sidebarDailyTasks)
        assertEquals(listOf(CalendarItem.EventItem(event)), state.sidebarTimedItems)
        assertTrue(state.copy(selectedDate = previous.dueDate).sidebarDailyTasks.isEmpty())
        assertEquals(listOf(previous), state.copy(selectedDate = previous.dueDate).sidebarCompletedTasks)
        assertTrue(state.copy(selectedDate = LocalDate(2026, 11, 8)).sidebarDailyTasks.isEmpty())
        assertTrue(state.monthTasksByDate.values.flatten().none { it.isDailyRoutine })
    }

    @Test
    fun `daily tasks stay in Daily while timed events including daily events stay in With time and the grid`() {
        val routine = daily(1)
        val alternate = Task(2, "Every other day", date, recurrence = dailyRule.copy(interval = 2))
        val weekly = Event(3, "Weekly review", date,
            recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Weekly, weekdays = setOf(DayOfWeek.FRIDAY)),
            startTime = LocalTime(10, 0))
        val meeting = Event(4, "Meeting", date, startTime = LocalTime(12, 0))
        val dailyEvent = Event(5, "Daily call", date, recurrence = dailyRule, startTime = LocalTime(8, 0))
        val state = CalendarState(date, tasks = listOf(routine, alternate), events = listOf(weekly, meeting, dailyEvent))
        assertEquals(listOf(routine), state.sidebarDailyTasks)
        assertEquals(listOf(5, 3, 4), state.sidebarTimedItems.map { it.id })
        assertEquals(listOf(alternate), state.sidebarUntimedTasks)
        val sections = state.sidebarDailyItems + state.sidebarTimedItems + state.sidebarUntimedItems
        assertEquals(state.sidebarItems.map { it.key }.toSet(), sections.map { it.key }.toSet())
        assertEquals(sections.size, sections.distinctBy { it.key }.size)
        assertEquals(listOf(5, 3, 4, 2), state.monthItemsByDate[date]?.map { it.id })
        assertFalse(alternate.isDailyRoutine)
    }

    @Test
    fun `routines never contribute to month cells or their overflow counts while no-date view is unchanged`() {
        val routines = (1..20).map { daily(it) }
        val undated = Task(30, "No date", createdAt = created)
        val state = CalendarState(date, tasks = routines + undated)
        assertTrue(state.monthTasksByDate.isEmpty())
        assertEquals(20, state.sidebarTasks.size)
        val noDate = state.copy(selectedDate = null)
        assertEquals(listOf(undated), noDate.sidebarTasks)
        assertTrue(noDate.sidebarDailyTasks.isEmpty())
        assertTrue(noDate.sidebarTimedItems.isEmpty())
        assertEquals(listOf(undated), noDate.sidebarUntimedTasks)
    }
}
