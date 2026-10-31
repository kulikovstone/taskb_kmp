package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.repository.GroupWriteResult
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarStoreTest {
    private val today = LocalDate(2026, 10, 30)
    private val created = Instant.parse("2026-10-01T10:00:00Z")

    @Test
    fun `undated tasks put recent completions last exclude older completions and preserve creation order`() {
        val now = Instant.parse("2026-10-30T10:00:00Z")
        val state = CalendarState(today, now = now, tasks = listOf(
            Task(1, "Old", createdAt = Instant.fromEpochMilliseconds(1)),
            Task(2, "New", createdAt = created), Task(3, "Completed", createdAt = created, completedAt = now - 1.hours),
            Task(4, "Scheduled completed", today, createdAt = created, completedAt = created),
            Task(5, "Newest tie", createdAt = created),
            Task(6, "Expired exactly at 24 hours", createdAt = now, completedAt = now - 24.hours),
            Task(7, "Just inside the window", createdAt = created - 1.milliseconds, completedAt = now - 24.hours + 1.milliseconds),
            Task(8, "Older completion", createdAt = now, completedAt = now - 25.hours),
        ))
        assertEquals(listOf(5, 2, 1, 3, 7), state.undatedTasks.map { it.id })
        assertEquals(listOf(4), state.tasksByDate.values.flatten().map { it.id })
    }

    @Test
    fun `recent completions expire while open and reopening or completing tasks updates the deadline`() = runTest {
        val start = Instant.parse("2026-10-30T10:00:00Z")
        val clock = object : Clock { override fun now() = start + testScheduler.currentTime.milliseconds }
        val task = Task(1, "Recently completed", createdAt = created, completedAt = start - 24.hours + 1_500.milliseconds)
        val active = Task(2, "Active", createdAt = created)
        val repository = Tasks(listOf(task, active), clock::now)
        CalendarStore(repository, Groups(), StandardTestDispatcher(testScheduler), { today }, clock).use { store ->
            runCurrent()
            assertEquals(listOf(2, 1), store.state.value.undatedTasks.map { it.id })
            advanceTimeBy(1_499)
            runCurrent()
            assertEquals(listOf(2, 1), store.state.value.undatedTasks.map { it.id })
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(2), store.state.value.undatedTasks.map { it.id })
            assertEquals(listOf(task, active), repository.values.value)

            store.accept(CalendarIntent.ToggleCompleted(1))
            runCurrent()
            assertEquals(listOf(2, 1), store.state.value.undatedTasks.map { it.id })
            assertNull(store.state.value.nextCompletionExpiry)
            store.accept(CalendarIntent.ToggleCompleted(2))
            runCurrent()
            assertEquals(listOf(1, 2), store.state.value.undatedTasks.map { it.id })
            assertEquals(clock.now() + 24.hours, store.state.value.nextCompletionExpiry)

            repository.values.value = repository.values.value.map {
                if (it.id == 2) it.copy(completedAt = clock.now() - 24.hours + 500.milliseconds) else it
            }
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(listOf(1), store.state.value.undatedTasks.map { it.id })
        }
        runCurrent()
        assertEquals(0, repository.subscriptions)
    }

    @Test
    fun `grid covers complete Monday first weeks including leap days and adjacent years`() {
        val february = CalendarState(today, month = YearMonth(2024, 2)).days
        assertEquals(LocalDate(2024, 1, 29), february.first())
        assertEquals(LocalDate(2024, 3, 3), february.last())
        assertTrue(LocalDate(2024, 2, 29) in february)
        assertEquals(28, CalendarState(today, month = YearMonth(2027, 2)).days.size)
        assertEquals(42, CalendarState(today, month = YearMonth(2026, 3)).days.size)
        assertEquals(LocalDate(2027, 1, 3), CalendarState(today, month = YearMonth(2026, 12)).days.last())
    }

    @Test
    fun `moving an undated task and undoing changes only its date and preserves concurrent edits`() = runTest {
        val original = Task(1, "Original", groupId = 7, createdAt = created, completedAt = created)
        val repository = Tasks(listOf(original))
        CalendarStore(repository, Groups(), StandardTestDispatcher(testScheduler), { today }).use { store ->
            runCurrent()
            store.accept(CalendarIntent.MoveTask(1, today))
            runCurrent()
            assertEquals(original.copy(dueDate = today), repository.values.value.single())
            assertEquals(1, store.state.value.undo?.taskId)
            repository.values.value = listOf(repository.values.value.single().copy(name = "Renamed", groupId = 8, completedAt = null))
            runCurrent()
            store.accept(CalendarIntent.UndoMove)
            runCurrent()
            assertEquals(original.copy(name = "Renamed", groupId = 8, completedAt = null), repository.values.value.single())
            assertNull(store.state.value.undo)
            assertNull(store.state.value.pendingMove)
        }
        runCurrent()
        assertEquals(0, repository.subscriptions)
    }

    @Test
    fun `scheduled moves restore the previous date and only the latest move can be undone for three seconds`() = runTest {
        val previous = LocalDate(2026, 9, 30)
        val repository = Tasks(listOf(Task(1, "Scheduled", previous), Task(2, "Undated")))
        CalendarStore(repository, Groups(), StandardTestDispatcher(testScheduler), { today }).use { store ->
            runCurrent()
            store.accept(CalendarIntent.MoveTask(1, today))
            runCurrent()
            advanceTimeBy(2_000)
            store.accept(CalendarIntent.MoveTask(2, today))
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, store.state.value.undo?.taskId)
            store.accept(CalendarIntent.UndoMove)
            runCurrent()
            assertNull(repository.getById(2)?.dueDate)
            store.accept(CalendarIntent.MoveTask(1, previous))
            runCurrent()
            store.accept(CalendarIntent.UndoMove)
            runCurrent()
            assertEquals(today, repository.getById(1)?.dueDate)
            store.accept(CalendarIntent.MoveTask(1, previous))
            runCurrent()
            advanceTimeBy(2_999)
            runCurrent()
            assertNotNull(store.state.value.undo)
            advanceTimeBy(1)
            runCurrent()
            assertNull(store.state.value.undo)
            store.accept(CalendarIntent.UndoMove)
            runCurrent()
            assertEquals(previous, repository.getById(1)?.dueDate)
        }
    }

    @Test
    fun `same day drops are ignored duplicate pending moves are rejected and failed writes never offer undo`() = runTest {
        val repository = Tasks(listOf(Task(1, "Task", today)))
        CalendarStore(repository, Groups(), StandardTestDispatcher(testScheduler), { today }).use { store ->
            runCurrent()
            store.accept(CalendarIntent.MoveTask(1, today))
            runCurrent()
            assertEquals(0, repository.writes)
            val gate = CompletableDeferred<Unit>()
            repository.beforeMove = { gate.await() }
            store.accept(CalendarIntent.MoveTask(1, LocalDate(2026, 11, 1)))
            store.accept(CalendarIntent.MoveTask(1, LocalDate(2026, 11, 2)))
            runCurrent()
            assertEquals(1, repository.writes)
            assertNotNull(store.state.value.pendingMove)
            repository.fail = true
            gate.complete(Unit)
            runCurrent()
            assertNull(store.state.value.undo)
            assertNotNull(store.state.value.error)
            assertNull(store.state.value.pendingMove)
            assertEquals(today, repository.values.value.single().dueDate)
        }
    }

    @Test
    fun `undo does not overwrite a newer date and moving a deleted task fails safely`() = runTest {
        val repository = Tasks(listOf(Task(1, "Task")))
        CalendarStore(repository, Groups(), StandardTestDispatcher(testScheduler), { today }).use { store ->
            runCurrent()
            store.accept(CalendarIntent.MoveTask(1, today))
            runCurrent()
            val newer = LocalDate(2027, 1, 1)
            repository.values.value = listOf(repository.values.value.single().copy(dueDate = newer))
            runCurrent()
            store.accept(CalendarIntent.UndoMove)
            runCurrent()
            assertEquals(newer, repository.values.value.single().dueDate)
            assertNotNull(store.state.value.error)
            repository.beforeMove = { repository.values.value = emptyList() }
            store.accept(CalendarIntent.MoveTask(1, today))
            runCurrent()
            assertTrue(repository.values.value.isEmpty())
            assertNull(store.state.value.undo)
            assertNotNull(store.state.value.error)
        }
    }

    @Test
    fun `load retries completion failures month changes and local day refresh preserve navigation state`() = runTest {
        var date = today
        val repository = Tasks(listOf(Task(1, "Task"))).apply { fail = true }
        val groups = Groups()
        CalendarStore(repository, groups, StandardTestDispatcher(testScheduler), { date }).use { store ->
            runCurrent()
            assertNotNull(store.state.value.loadError)
            repository.fail = false
            store.accept(CalendarIntent.RetryLoad)
            store.accept(CalendarIntent.PreviousMonth)
            runCurrent()
            assertNull(store.state.value.loadError)
            assertEquals(YearMonth(2026, 9), store.state.value.month)
            date = LocalDate(2026, 11, 1)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(date, store.state.value.today)
            assertEquals(YearMonth(2026, 9), store.state.value.month)
            store.accept(CalendarIntent.Today)
            store.accept(CalendarIntent.NextMonth)
            store.accept(CalendarIntent.ToggleCompleted(1))
            runCurrent()
            assertEquals(YearMonth(2026, 12), store.state.value.month)
            assertTrue(repository.values.value.single().isCompleted)
            assertEquals(listOf(1), store.state.value.undatedTasks.map { it.id })
            repository.fail = true
            store.accept(CalendarIntent.ToggleCompleted(1))
            runCurrent()
            assertNotNull(store.state.value.error)
            assertTrue(store.state.value.pendingCompletion.isEmpty())
        }
        runCurrent()
        assertEquals(0, repository.subscriptions)
        assertEquals(0, groups.subscriptions)
    }

    private class Tasks(tasks: List<Task>, private val now: () -> Instant = Clock.System::now) : ITaskRepository {
        val values = MutableStateFlow(tasks)
        var fail = false
        var writes = 0
        var subscriptions = 0
        var beforeMove: suspend () -> Unit = {}
        override val tasks = flow {
            if (fail) error("Read failed")
            subscriptions++
            try { emitAll(values) } finally { subscriptions-- }
        }
        override suspend fun getById(id: Int) = values.value.find { it.id == id }
        override suspend fun save(task: Task): Boolean = error("Unused")
        override suspend fun delete(id: Int) { values.value = values.value.filter { it.id != id } }
        override suspend fun setCompleted(id: Int, completed: Boolean) {
            if (fail) error("Write failed")
            values.value = values.value.map { if (it.id == id) it.copy(completedAt = if (completed) now() else null) else it }
        }
        override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean {
            writes++
            beforeMove()
            if (fail) error("Write failed")
            if (values.value.none { it.id == id && it.dueDate == expectedDate }) return false
            values.value = values.value.map { if (it.id == id) it.copy(dueDate = date) else it }
            return true
        }
    }

    private class Groups : IGroupRepository {
        var subscriptions = 0
        override val groups = flow<List<Group>> {
            subscriptions++
            try { emitAll(MutableStateFlow(emptyList<Group>())) } finally { subscriptions-- }
        }
        override suspend fun getById(id: Int): Group? = null
        override suspend fun add(name: String, color: GroupColor): GroupWriteResult = error("Unused")
        override suspend fun update(group: Group): GroupWriteResult = error("Unused")
        override suspend fun delete(id: Int) = Unit
    }
}
