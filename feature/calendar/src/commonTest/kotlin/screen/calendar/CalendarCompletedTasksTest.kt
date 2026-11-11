package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class CalendarCompletedTasksTest {
    private val date = LocalDate(year = 2026, month = 11, day = 6)
    private val now = Instant.parse(input = "2026-11-06T12:00:00Z")
    private val daily = TaskRecurrence(frequency = RecurrenceFrequency.Daily)

    @Test
    fun `completed tasks leave all active sections and sort by completion instead of creation or start time`() {
        val routine = Task(id = 1, name = "Routine", dueDate = date, createdAt = now, recurrence = daily)
        val event = Task(id = 2, name = "Meeting", dueDate = date, createdAt = now)
        val task = Task(id = 3, name = "Bills", dueDate = date, createdAt = now)
        val completedRoutine = routine.copy(
            id = 4,
            completedAt = now - 2.hours,
            occurrenceId = TaskOccurrenceId(scheduleId = 4, scheduledDate = date),
        )
        val completedEvent = event.copy(id = 5, createdAt = now - 100.hours, completedAt = now - 1.hours)
        val completedTask = task.copy(id = 6, createdAt = now + 1.hours, completedAt = now - 48.hours)
        val state = CalendarState(
            today = date,
            now = now,
            tasks = listOf(
                completedTask,
                event,
                completedRoutine,
                task,
                completedEvent,
                routine,
                task.copy(id = 7, dueDate = LocalDate(year = 2026, month = 11, day = 7), completedAt = now),
                task.copy(id = 8, dueDate = null, completedAt = now),
            ),
        )

        assertEquals(expected = listOf(element = routine), actual = state.sidebarRecurringTasks)
        assertTrue(actual = state.sidebarEventItems.isEmpty())
        assertEquals(expected = listOf(task, event), actual = state.sidebarUntimedTasks)
        assertEquals(
            expected = listOf(completedEvent, completedRoutine, completedTask),
            actual = state.sidebarCompletedTasks,
        )
        val sections = state.sidebarRecurringTasks + state.sidebarUntimedTasks + state.sidebarCompletedTasks
        assertEquals(
            expected = state.sidebarTasks.map(transform = Task::key).toSet(),
            actual = sections.map(transform = Task::key).toSet(),
        )
        assertEquals(expected = sections.size, actual = sections.distinctBy(selector = Task::key).size)
        assertTrue(
            actual = state.monthTasksByDate[date].orEmpty().containsAll(
                elements = listOf(completedEvent, completedTask),
            )
        )
    }

    @Test
    fun `no date completed section keeps the existing visibility window and orders recent completions first`() {
        val active = Task(id = 1, name = "Active", createdAt = now)
        val older = Task(id = 2, name = "Older", createdAt = now, completedAt = now - 23.hours)
        val recent = Task(id = 3, name = "Recent", createdAt = now - 100.hours, completedAt = now - 1.hours)
        val state = CalendarState(
            today = date,
            selectedDate = null,
            now = now,
            tasks = listOf(
                active,
                older,
                recent,
                older.copy(id = 4, completedAt = now - 24.hours),
                older.copy(id = 5, completedAt = now - 25.hours),
                recent.copy(id = 6, dueDate = date),
            ),
        )

        assertTrue(actual = state.sidebarRecurringTasks.isEmpty())
        assertTrue(actual = state.sidebarEventItems.isEmpty())
        assertEquals(expected = listOf(element = active), actual = state.sidebarUntimedTasks)
        assertEquals(expected = listOf(recent, older), actual = state.sidebarCompletedTasks)
        assertEquals(
            expected = listOf(element = recent),
            actual = state.copy(now = now + 1.hours).sidebarCompletedTasks,
        )
    }
}
