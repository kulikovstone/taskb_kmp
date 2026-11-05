package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CalendarCompletedTasksTest {
    private val date = LocalDate(2026, 11, 6)
    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val daily = TaskRecurrence(frequency = RecurrenceFrequency.Daily)

    @Test
    fun `completed tasks leave all active sections and sort by completion instead of creation or start time`() {
        val routine = Task(1, "Routine", date, createdAt = now, recurrence = daily)
        val event = Task(2, "Meeting", date, createdAt = now)
        val task = Task(3, "Bills", date, createdAt = now)
        val completedRoutine = routine.copy(id = 4, completedAt = now - 2.hours, occurrenceId = TaskOccurrenceId(4, date))
        val completedEvent = event.copy(id = 5, createdAt = now - 100.hours, completedAt = now - 1.hours)
        val completedTask = task.copy(id = 6, createdAt = now + 1.hours, completedAt = now - 48.hours)
        val state = CalendarState(date, now = now, tasks = listOf(
            completedTask, event, completedRoutine, task, completedEvent, routine,
            task.copy(id = 7, dueDate = LocalDate(2026, 11, 7), completedAt = now),
            task.copy(id = 8, dueDate = null, completedAt = now),
        ))

        assertEquals(listOf(routine), state.sidebarDailyTasks)
        assertTrue(state.sidebarEventItems.isEmpty())
        assertEquals(listOf(task, event), state.sidebarUntimedTasks)
        assertEquals(listOf(completedEvent, completedRoutine, completedTask), state.sidebarCompletedTasks)
        val sections = state.sidebarDailyTasks + state.sidebarUntimedTasks + state.sidebarCompletedTasks
        assertEquals(state.sidebarTasks.map { it.key }.toSet(), sections.map { it.key }.toSet())
        assertEquals(sections.size, sections.distinctBy { it.key }.size)
        assertTrue(state.monthTasksByDate[date].orEmpty().containsAll(listOf(completedEvent, completedTask)))
    }

    @Test
    fun `no date completed section keeps the existing visibility window and orders recent completions first`() {
        val active = Task(1, "Active", createdAt = now)
        val older = Task(2, "Older", createdAt = now, completedAt = now - 23.hours)
        val recent = Task(3, "Recent", createdAt = now - 100.hours, completedAt = now - 1.hours)
        val state = CalendarState(date, selectedDate = null, now = now, tasks = listOf(
            active, older, recent,
            older.copy(id = 4, completedAt = now - 24.hours),
            older.copy(id = 5, completedAt = now - 25.hours),
            recent.copy(id = 6, dueDate = date),
        ))

        assertTrue(state.sidebarDailyTasks.isEmpty())
        assertTrue(state.sidebarEventItems.isEmpty())
        assertEquals(listOf(active), state.sidebarUntimedTasks)
        assertEquals(listOf(recent, older), state.sidebarCompletedTasks)
        assertEquals(listOf(recent), state.copy(now = now + 1.hours).sidebarCompletedTasks)
    }
}
