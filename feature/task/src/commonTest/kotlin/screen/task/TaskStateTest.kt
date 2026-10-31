package com.mkulikov.taskb.feature.task.screen.task

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class TaskStateTest {
    private val today = LocalDate(2026, 10, 31)
    private val now = Instant.parse("2026-10-31T12:00:00Z")
    private val groups = listOf(Group(1, "Zulu", GroupColor.Blue), Group(2, "alpha", GroupColor.Green))

    @Test
    fun `date sorting keeps newest tasks first within each date in both directions`() {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val state = TaskState(today = today, now = now, tasks = listOf(
            Task(40, "Alphabetically first", today, createdAt = now - 3.hours),
            Task(9, "Newest today", today, createdAt = now - 1.hours),
            Task(2, "Old tomorrow", tomorrow, createdAt = now - 2.hours),
            Task(3, "New tomorrow", tomorrow, createdAt = now),
            Task(1, "Old undated", createdAt = now - 3.hours),
            Task(50, "New undated", createdAt = now),
            Task(7, "Yesterday", today.minus(1, DateTimeUnit.DAY), createdAt = now - 10.hours),
        ))

        assertEquals(TaskSort.Date, state.sort)
        assertEquals(listOf(7, 9, 40, 3, 2, 50, 1), state.visibleTasks.map { it.id })
        assertEquals(listOf(50, 1, 3, 2, 9, 40, 7), state.copy(reverseSort = true).visibleTasks.map { it.id })
    }

    @Test
    fun `group sorting reverses only group order and preserves newest first within each group`() {
        val state = TaskState(today = today, now = now, groups = groups, sort = TaskSort.Group, tasks = listOf(
            Task(99, "AAA old Zulu", today, 1, createdAt = now - 3.hours),
            Task(3, "New Zulu", today, 1, createdAt = now - 1.hours),
            Task(80, "AAA old alpha", today, 2, createdAt = now - 2.hours),
            Task(2, "New alpha", today, 2, createdAt = now),
            Task(70, "AAA old no group", today, createdAt = now - 2.hours),
            Task(10, "New no group", today, createdAt = now - 1.hours),
        ))

        assertEquals(listOf(10, 70, 2, 80, 3, 99), state.visibleTasks.map { it.id })
        assertEquals(listOf(3, 99, 2, 80, 10, 70), state.copy(reverseSort = true).visibleTasks.map { it.id })
    }

    @Test
    fun `name sorting is case insensitive and keeps newest first for equal names in both directions`() {
        val state = TaskState(today = today, now = now, sort = TaskSort.Name, tasks = listOf(
            Task(1, "Beta", createdAt = now),
            Task(3, "alpha", createdAt = now - 2.hours),
            Task(2, "ALPHA", createdAt = now - 1.hours),
        ))

        assertEquals(listOf(2, 3, 1), state.visibleTasks.map { it.id })
        assertEquals(listOf(1, 2, 3), state.copy(reverseSort = true).visibleTasks.map { it.id })
    }

    @Test
    fun `creation timestamp ties use newest id regardless of primary sort direction`() {
        val tasks = listOf(3, 9, 5).map { Task(it, "Same name", today, 1, createdAt = now) } +
            Task(100, "Same name", today, 1, createdAt = now - 1.milliseconds)
        for (sort in TaskSort.entries) for (reverse in listOf(false, true)) {
            val state = TaskState(today = today, now = now, tasks = tasks, groups = groups,
                sort = sort, reverseSort = reverse)
            assertEquals(listOf(9, 5, 3, 100), state.visibleTasks.map { it.id }, "$sort reverse=$reverse")
        }
    }

    @Test
    fun `only completions younger than 24 hours appear and always follow active tasks`() {
        val tasks = listOf(
            Task(1, "Zulu", today.plus(1, DateTimeUnit.DAY), 1, createdAt = now - 1.hours),
            Task(2, "Alpha", today, 2, createdAt = now - 2.hours),
            Task(3, "Alpha completed", today.minus(1, DateTimeUnit.DAY), 2,
                createdAt = now, completedAt = now - 1.hours),
            Task(4, "Zulu completed", today.plus(1, DateTimeUnit.DAY), 1,
                createdAt = now - 3.hours, completedAt = now - 2.hours),
            Task(5, "Exactly 24 hours old", today, createdAt = now, completedAt = now - 24.hours),
            Task(6, "Older undated completion", createdAt = now, completedAt = now - 25.hours),
            Task(7, "Just inside window", createdAt = now - 4.hours,
                completedAt = now - 24.hours + 1.milliseconds),
        )
        val state = TaskState(today = today, now = now, tasks = tasks, groups = groups)
        for (sort in TaskSort.entries) for (reverse in listOf(false, true)) {
            val visible = state.copy(sort = sort, reverseSort = reverse).visibleTasks
            assertEquals(listOf(false, false, true, true, true), visible.map { it.isCompleted }, "$sort reverse=$reverse")
            assertEquals(setOf(1, 2, 3, 4, 7), visible.map { it.id }.toSet())
        }
        assertEquals(setOf(3, 4, 7), state.copy(filter = TaskFilter.Completed).visibleTasks.map { it.id }.toSet())
        assertEquals(listOf(7), state.copy(groupFilter = TaskGroupFilter.NoGroup).visibleTasks.map { it.id })
        assertEquals(now + 1.milliseconds, state.nextCompletionExpiry)
        assertEquals(7, state.tasks.size)
    }

    @Test
    fun `counts and filter summaries exclude expired tasks without removing stored data`() {
        val old = Task(1, "Old completion", today, createdAt = now - 25.hours, completedAt = now - 24.hours)
        val active = Task(2, "Active", today, createdAt = now)
        val state = TaskState(today = today, now = now, tasks = listOf(old, active))

        assertEquals(1, state.taskCount)
        assertEquals("1 task", state.summary)
        assertEquals("1 of 1 tasks", state.copy(query = "Active").summary)
        assertEquals("0 of 1 tasks", state.copy(filter = TaskFilter.Completed).summary)
        assertEquals("0 tasks", state.copy(tasks = listOf(old)).summary)
        assertEquals(2, state.tasks.size)
    }
}
