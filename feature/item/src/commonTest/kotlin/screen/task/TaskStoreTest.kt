package com.mkulikov.taskb.feature.item.screen.task

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.api.model.TaskFilter
import com.mkulikov.taskb.feature.item.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.item.testing.FakeTaskRepository
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@OptIn(ExperimentalCoroutinesApi::class)
class TaskStoreTest {
    private val today = LocalDate(year = 2026, month = 10, day = 25)
    private val now = Instant.parse(input = "2026-10-25T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = this@TaskStoreTest.now
        }
    private val tasks = listOf(
        Task(id = 1, name = "Overdue", dueDate = today.minus(value = 1, unit = DateTimeUnit.DAY), groupId = 1),
        Task(id = 2, name = "Today", dueDate = today, groupId = 1),
        Task(id = 3, name = "Tomorrow", dueDate = today.plus(value = 1, unit = DateTimeUnit.DAY)),
        Task(id = 4, name = "Later", dueDate = today.plus(value = 2, unit = DateTimeUnit.DAY)),
        Task(id = 5, name = "Someday"),
        Task(
            id = 6,
            name = "Completed",
            dueDate = today,
            groupId = 1,
            completedAt = Instant.parse(input = "2026-10-25T10:00:00Z"),
        ),
    )

    @Test
    fun dateFiltersRefreshWhenTheLocalDayChanges(): TestResult {
        return runTest {
            var date = today
            val repository = FakeTaskRepository().apply { values.value = this@TaskStoreTest.tasks }
            TaskStore(
                    repository = repository,
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { date },
                    clock = clock,
                )
                .use { store ->
                    store.accept(intent = TaskIntent.Filter(filter = TaskFilter.Overdue))
                    runCurrent()
                    assertEquals(
                        expected = listOf(element = 1),
                        actual = store.state.value.visibleTasks.map(transform = Task::id),
                    )
                    date = today.plus(value = 1, unit = DateTimeUnit.DAY)
                    advanceTimeBy(delayTimeMillis = 60_000)
                    runCurrent()
                    assertEquals(expected = date, actual = store.state.value.today)
                    assertEquals(
                        expected = listOf(1, 2),
                        actual = store.state.value.visibleTasks.map(transform = Task::id),
                    )
                }
            runCurrent()
            assertEquals(expected = 0, actual = repository.subscriptions)
        }
    }

    @Test
    fun `date filters exclude completed tasks and combine with group and case insensitive search`() {
        val state = TaskState(tasks = tasks, today = today, now = now)
        val expected = mapOf(
            TaskFilter.Scheduled to listOf(1, 2, 3, 4),
            TaskFilter.Overdue to listOf(element = 1),
            TaskFilter.Today to listOf(element = 2),
            TaskFilter.Tomorrow to listOf(element = 3),
            TaskFilter.OtherDates to listOf(element = 4),
            TaskFilter.NoDate to listOf(element = 5),
            TaskFilter.Completed to listOf(element = 6),
        )
        expected.forEach { (filter, ids) ->
            assertEquals(
                expected = ids,
                actual = state.copy(filter = filter).visibleTasks.map(transform = Task::id),
                message = filter.label,
            )
        }
        assertEquals(
            expected = listOf(1, 2, 6),
            actual = state.copy(groupFilter = TaskGroupFilter.Selected(id = 1)).visibleTasks.map(transform = Task::id),
        )
        assertEquals(
            expected = listOf(3, 4, 5),
            actual = state.copy(groupFilter = TaskGroupFilter.NoGroup).visibleTasks.map(transform = Task::id),
        )
        assertEquals(
            expected = listOf(element = 2),
            actual = state
                .copy(
                    groupFilter = TaskGroupFilter.Selected(id = 1),
                    query = TextFieldState(initialText = " TODAY "),
                    filter = TaskFilter.Scheduled,
                )
                .visibleTasks
                .map(transform = Task::id),
        )
        assertEquals(
            expected = 6,
            actual = state.copy(sort = TaskSort.Name, reverseSort = true).visibleTasks.last().id,
        )
        assertEquals(
            expected = listOf(1, 2),
            actual = state
                .copy(filter = TaskFilter.Overdue, today = today.plus(value = 1, unit = DateTimeUnit.DAY))
                .visibleTasks
                .map(transform = Task::id),
        )
    }

    @Test
    fun `store retries loading applies filters updates completion and releases subscriptions`(): TestResult {
        return runTest {
            val repository = FakeTaskRepository(clock = clock).apply {
                fail = true
                values.value = this@TaskStoreTest.tasks
            }
            val groups = FakeGroupRepository().apply {
                values.value = listOf(element = Group(id = 1, name = "Work", color = GroupColor.Blue))
            }
            val store = TaskStore(
                repository = repository,
                groups = groups,
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                today = { today },
                clock = clock,
            )
            runCurrent()
            assertNotNull(actual = store.state.value.loadError)
            repository.fail = false
            store.accept(intent = TaskIntent.RetryLoad)
            runCurrent()
            assertEquals(expected = 6, actual = store.state.value.tasks.size)
            store.accept(intent = TaskIntent.Choose(selection = TaskSelection.Filters))
            store.accept(intent = TaskIntent.Filter(filter = TaskFilter.Completed))
            store.accept(intent = TaskIntent.ToggleCompleted(id = 1))
            runCurrent()
            assertNull(actual = store.state.value.selection)
            assertEquals(expected = 2, actual = store.state.value.visibleTasks.size)
            store.accept(intent = TaskIntent.FilterGroup(filter = TaskGroupFilter.Selected(id = 1)))
            runCurrent()
            groups.values.value = emptyList()
            runCurrent()
            assertEquals(expected = TaskGroupFilter.All, actual = store.state.value.groupFilter)
            repository.fail = true
            store.accept(intent = TaskIntent.ToggleCompleted(id = 2))
            runCurrent()
            assertNotNull(actual = store.state.value.error)
            assertTrue(actual = store.state.value.pendingCompletion.isEmpty())
            store.close()
            runCurrent()
            assertEquals(expected = 0, actual = repository.subscriptions)
            assertEquals(expected = 0, actual = groups.subscriptions)
        }
    }

    @Test
    fun `completed tasks expire while open and reopening or new completions reschedule expiry`(): TestResult {
        return runTest {
            val start = now
            val clock =
                object : Clock {
                    override fun now() = start + testScheduler.currentTime.milliseconds
                }
            val scheduled = Task(
                id = 1,
                name = "Scheduled completion",
                dueDate = today,
                createdAt = start - 2.hours,
                completedAt = start - 24.hours + 1_500.milliseconds,
            )
            val undated = Task(
                id = 2,
                name = "Undated completion",
                createdAt = start - 1.hours,
                completedAt = start - 24.hours + 2_500.milliseconds,
            )
            val active = Task(id = 3, name = "Active", dueDate = today, createdAt = start)
            val repository = FakeTaskRepository(clock = clock).apply {
                values.value = listOf(scheduled, undated, active)
            }
            val groups = FakeGroupRepository()
            TaskStore(
                    repository = repository,
                    groups = groups,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                    clock = clock,
                )
                .use { store ->
                    store.accept(intent = TaskIntent.Filter(filter = TaskFilter.Completed))
                    runCurrent()
                    assertEquals(
                        expected = listOf(1, 2),
                        actual = store.state.value.visibleTasks.map(transform = Task::id),
                    )
                    advanceTimeBy(delayTimeMillis = 1_499)
                    runCurrent()
                    assertEquals(
                        expected = listOf(1, 2),
                        actual = store.state.value.visibleTasks.map(transform = Task::id),
                    )
                    advanceTimeBy(delayTimeMillis = 1)
                    runCurrent()
                    assertEquals(
                        expected = listOf(element = 2),
                        actual = store.state.value.visibleTasks.map(transform = Task::id),
                    )
                    assertEquals(expected = listOf(scheduled, undated, active), actual = repository.values.value)
                    assertEquals(expected = "1 of 2 tasks", actual = store.state.value.summary)

                    store.accept(intent = TaskIntent.Filter(filter = null))
                    store.accept(intent = TaskIntent.ToggleCompleted(id = 1))
                    store.accept(intent = TaskIntent.ToggleCompleted(id = 3))
                    runCurrent()
                    assertEquals(
                        expected = listOf(1, 3, 2),
                        actual = store.state.value.visibleTasks.map(transform = Task::id),
                    )
                    assertEquals(expected = start + 2_500.milliseconds, actual = store.state.value.nextCompletionExpiry)

                    repository.values.value = repository.values.value.map { task ->
                        if (task.id == 3) task.copy(completedAt = clock.now() - 24.hours + 500.milliseconds) else task
                    }
                    runCurrent()
                    assertEquals(
                        expected = clock.now() + 500.milliseconds,
                        actual = store.state.value.nextCompletionExpiry,
                    )
                    advanceTimeBy(delayTimeMillis = 500)
                    runCurrent()
                    assertEquals(
                        expected = listOf(1, 2),
                        actual = store.state.value.visibleTasks.map(transform = Task::id),
                    )
                    advanceTimeBy(delayTimeMillis = 500)
                    runCurrent()
                    assertEquals(
                        expected = listOf(element = 1),
                        actual = store.state.value.visibleTasks.map(transform = Task::id),
                    )
                    assertNull(actual = store.state.value.nextCompletionExpiry)
                    assertEquals(expected = "1 task", actual = store.state.value.summary)
                    assertEquals(expected = 3, actual = repository.values.value.size)
                }
            runCurrent()
            assertEquals(expected = 0, actual = repository.subscriptions)
            assertEquals(expected = 0, actual = groups.subscriptions)
        }
    }
}
