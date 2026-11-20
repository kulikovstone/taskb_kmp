package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskFilter
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class TaskStateTest {
    private val today = LocalDate(year = 2026, month = 10, day = 31)
    private val now = Instant.parse(input = "2026-10-31T12:00:00Z")
    private val groups = listOf(
        Group(id = 1, name = "Zulu", color = GroupColor.Blue),
        Group(id = 2, name = "alpha", color = GroupColor.Green),
    )

    @Test
    fun `completed recurring events appear only in completed filter without hiding the next event`() {
        val first = Task(
            id = 1,
            name = "Series",
            dueDate = today,
            completedAt = now,
            occurrenceId = TaskOccurrenceId(scheduleId = 1, scheduledDate = today),
        )
        val next = first.copy(
            dueDate = today.plus(value = 1, unit = DateTimeUnit.DAY),
            completedAt = null,
            occurrenceId = TaskOccurrenceId(
                scheduleId = 1,
                scheduledDate = today.plus(value = 1, unit = DateTimeUnit.DAY),
            ),
        )
        val state = TaskState(today = today, now = now, tasks = listOf(first, next))
        assertEquals(expected = listOf(element = next), actual = state.visibleTasks)
        assertEquals(
            expected = listOf(element = first),
            actual = state.copy(filter = TaskFilter.Completed).visibleTasks,
        )
    }

    @Test
    fun `date sorting keeps newest tasks first within each date in both directions`() {
        val tomorrow = today.plus(value = 1, unit = DateTimeUnit.DAY)
        val state = TaskState(
            today = today,
            now = now,
            tasks = listOf(
                Task(id = 40, name = "Alphabetically first", dueDate = today, createdAt = now - 3.hours),
                Task(id = 9, name = "Newest today", dueDate = today, createdAt = now - 1.hours),
                Task(id = 2, name = "Old tomorrow", dueDate = tomorrow, createdAt = now - 2.hours),
                Task(id = 3, name = "New tomorrow", dueDate = tomorrow, createdAt = now),
                Task(id = 1, name = "Old undated", createdAt = now - 3.hours),
                Task(id = 50, name = "New undated", createdAt = now),
                Task(
                    id = 7,
                    name = "Yesterday",
                    dueDate = today.minus(value = 1, unit = DateTimeUnit.DAY),
                    createdAt = now - 10.hours,
                ),
            ),
        )

        assertEquals(expected = TaskSort.Date, actual = state.sort)
        assertEquals(expected = listOf(7, 9, 40, 3, 2, 50, 1), actual = state.visibleTasks.map(transform = Task::id))
        assertEquals(
            expected = listOf(50, 1, 3, 2, 9, 40, 7),
            actual = state.copy(reverseSort = true).visibleTasks.map(transform = Task::id),
        )
    }

    @Test
    fun `group sorting reverses only group order and preserves newest first within each group`() {
        val state = TaskState(
            today = today,
            now = now,
            groups = groups,
            sort = TaskSort.Group,
            tasks = listOf(
                Task(
                    id = 99,
                    name = "AAA old Zulu",
                    dueDate = today,
                    groupId = 1,
                    createdAt = now - 3.hours,
                ),
                Task(id = 3, name = "New Zulu", dueDate = today, groupId = 1, createdAt = now - 1.hours),
                Task(
                    id = 80,
                    name = "AAA old alpha",
                    dueDate = today,
                    groupId = 2,
                    createdAt = now - 2.hours,
                ),
                Task(id = 2, name = "New alpha", dueDate = today, groupId = 2, createdAt = now),
                Task(id = 70, name = "AAA old no group", dueDate = today, createdAt = now - 2.hours),
                Task(id = 10, name = "New no group", dueDate = today, createdAt = now - 1.hours),
            ),
        )

        assertEquals(expected = listOf(10, 70, 2, 80, 3, 99), actual = state.visibleTasks.map(transform = Task::id))
        assertEquals(
            expected = listOf(3, 99, 2, 80, 10, 70),
            actual = state.copy(reverseSort = true).visibleTasks.map(transform = Task::id),
        )
    }

    @Test
    fun `name sorting is case insensitive and keeps newest first for equal names in both directions`() {
        val state = TaskState(
            today = today,
            now = now,
            sort = TaskSort.Name,
            tasks = listOf(
                Task(id = 1, name = "Beta", createdAt = now),
                Task(id = 3, name = "alpha", createdAt = now - 2.hours),
                Task(id = 2, name = "ALPHA", createdAt = now - 1.hours),
            ),
        )

        assertEquals(expected = listOf(2, 3, 1), actual = state.visibleTasks.map(transform = Task::id))
        assertEquals(
            expected = listOf(1, 2, 3),
            actual = state.copy(reverseSort = true).visibleTasks.map(transform = Task::id),
        )
    }

    @Test
    fun `creation timestamp ties use newest id regardless of primary sort direction`() {
        val tasks = listOf(3, 9, 5).map { taskId ->
            Task(id = taskId, name = "Same name", dueDate = today, groupId = 1, createdAt = now)
        } + Task(id = 100, name = "Same name", dueDate = today, groupId = 1, createdAt = now - 1.milliseconds)
        for (sort in TaskSort.entries) for (reverse in listOf(false, true)) {
            val state = TaskState(
                today = today,
                now = now,
                tasks = tasks,
                groups = groups,
                sort = sort,
                reverseSort = reverse,
            )
            assertEquals(
                expected = listOf(9, 5, 3, 100),
                actual = state.visibleTasks.map(transform = Task::id),
                message = "$sort reverse=$reverse",
            )
        }
    }

    @Test
    fun `only completions younger than 24 hours appear and always follow active tasks`() {
        val tasks = listOf(
            Task(
                id = 1,
                name = "Zulu",
                dueDate = today.plus(value = 1, unit = DateTimeUnit.DAY),
                groupId = 1,
                createdAt = now - 1.hours,
            ),
            Task(id = 2, name = "Alpha", dueDate = today, groupId = 2, createdAt = now - 2.hours),
            Task(
                id = 3,
                name = "Alpha completed",
                dueDate = today.minus(value = 1, unit = DateTimeUnit.DAY),
                groupId = 2,
                createdAt = now,
                completedAt = now - 1.hours,
            ),
            Task(
                id = 4,
                name = "Zulu completed",
                dueDate = today.plus(value = 1, unit = DateTimeUnit.DAY),
                groupId = 1,
                createdAt = now - 3.hours,
                completedAt = now - 2.hours,
            ),
            Task(
                id = 5,
                name = "Exactly 24 hours old",
                dueDate = today,
                createdAt = now,
                completedAt = now - 24.hours,
            ),
            Task(id = 6, name = "Older undated completion", createdAt = now, completedAt = now - 25.hours),
            Task(
                id = 7,
                name = "Just inside window",
                createdAt = now - 4.hours,
                completedAt = now - 24.hours + 1.milliseconds,
            ),
        )
        val state = TaskState(today = today, now = now, tasks = tasks, groups = groups)
        for (sort in TaskSort.entries) for (reverse in listOf(false, true)) {
            val visible = state.copy(sort = sort, reverseSort = reverse).visibleTasks
            assertEquals(
                expected = listOf(false, false, true, true, true),
                actual = visible.map(transform = Task::isCompleted),
                message = "$sort reverse=$reverse",
            )
            assertEquals(expected = setOf(1, 2, 3, 4, 7), actual = visible.map(transform = Task::id).toSet())
        }
        assertEquals(
            expected = setOf(3, 4, 7),
            actual = state.copy(filter = TaskFilter.Completed).visibleTasks.map(transform = Task::id).toSet(),
        )
        assertEquals(
            expected = listOf(element = 7),
            actual = state.copy(groupFilter = TaskGroupFilter.NoGroup).visibleTasks.map(transform = Task::id),
        )
        assertEquals(expected = now + 1.milliseconds, actual = state.nextCompletionExpiry)
        assertEquals(expected = 7, actual = state.tasks.size)
    }

    @Test
    fun `counts and filter summaries exclude expired tasks without removing stored data`() {
        val old = Task(
            id = 1,
            name = "Old completion",
            dueDate = today,
            createdAt = now - 25.hours,
            completedAt = now - 24.hours,
        )
        val active = Task(id = 2, name = "Active", dueDate = today, createdAt = now)
        val state = TaskState(today = today, now = now, tasks = listOf(old, active))

        assertEquals(expected = 1, actual = state.taskCount)
        assertEquals(expected = "1 task", actual = state.summary)
        assertEquals(
            expected = "1 of 1 tasks",
            actual = state.copy(query = TextFieldState(initialText = "Active")).summary,
        )
        assertEquals(expected = "0 of 1 tasks", actual = state.copy(filter = TaskFilter.Completed).summary)
        assertEquals(expected = "0 tasks", actual = state.copy(tasks = listOf(element = old)).summary)
        assertEquals(expected = 2, actual = state.tasks.size)
    }
}
