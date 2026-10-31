package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorStoreTest {
    private fun groups() = FakeGroupRepository().apply { values.value = listOf(Group(7, "Work", GroupColor.Blue)) }
    @Test
    fun `creation validates trims dates and group while preventing duplicate writes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeTaskRepository().apply { onWrite = { gate.await() } }
        TaskEditorStore(repo, groups(), dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            store.state.value.name.setTextAndPlaceCursorAtEnd("  ")
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertEquals(0, repo.writes)
            store.state.value.name.setTextAndPlaceCursorAtEnd(" Task ")
            store.accept(TaskEditorIntent.DateChanged(LocalDate(2026, 1, 31)))
            store.accept(TaskEditorIntent.ShiftDate(months = 1))
            store.accept(TaskEditorIntent.GroupChanged(7))
            repeat(3) { store.accept(TaskEditorIntent.Save) }
            runCurrent()
            assertEquals(1, repo.writes)
            assertEquals(" Task ", store.state.value.name.text.toString())
            gate.complete(Unit)
            runCurrent()
            val task = assertNotNull(repo.lastWrite)
            assertEquals("Task", task.name)
            assertEquals(LocalDate(2026, 2, 28), task.dueDate)
            assertEquals(7, task.groupId)
            assertNull(task.completedAt)
            assertTrue(store.state.value.isFinished)
        }
    }
    @Test
    fun `failed saving preserves draft and deletion needs confirmation`() = runTest {
        val repo = FakeTaskRepository().apply { values.value = listOf(Task(7, "Original", completedAt = Instant.parse("2026-10-25T10:00:00Z"))) }
        TaskEditorStore(repo, groups(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertFalse(store.state.value.canSave)
            store.state.value.name.setTextAndPlaceCursorAtEnd("Draft")
            repo.fail = true
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertIs<TaskEditorDialog.Error>(store.state.value.dialog)
            assertEquals("Draft", store.state.value.name.text.toString())
            store.accept(TaskEditorIntent.DismissDialog)
            store.accept(TaskEditorIntent.ConfirmDelete)
            runCurrent()
            assertEquals(0, repo.deletions)
            store.accept(TaskEditorIntent.PrepareDelete)
            repeat(3) { store.accept(TaskEditorIntent.ConfirmDelete) }
            runCurrent()
            assertEquals(1, repo.deletions)
            assertTrue(store.state.value.isFinished)
        }
    }
    @Test
    fun `missing and failed loads cannot save and failed loads can retry`() = runTest {
        val repo = FakeTaskRepository().apply { fail = true }
        TaskEditorStore(repo, groups(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertNotNull(store.state.value.loadError)
            assertFalse(store.state.value.canSave)
            repo.fail = false
            repo.values.value = listOf(Task(7, "Recovered"))
            store.accept(TaskEditorIntent.RetryLoad)
            runCurrent()
            assertNull(store.state.value.loadError)
            assertEquals("Recovered", store.state.value.name.text.toString())
        }
    }
    @Test
    fun `standalone editor retries groups keeps its draft and releases subscriptions`() = runTest {
        val groups = groups().apply { failRead = true }
        val repo = FakeTaskRepository()
        var today = LocalDate(2026, 10, 28)
        TaskEditorStore(repo, groups, initialDate = today, initialGroup = 7,
            dispatcher = StandardTestDispatcher(testScheduler), today = { today }).use { store ->
            runCurrent()
            assertNotNull(store.state.value.groupsLoadError)
            store.state.value.name.setTextAndPlaceCursorAtEnd("Draft")
            assertFalse(store.state.value.canSave)
            groups.failRead = false
            store.accept(TaskEditorIntent.RetryGroups)
            runCurrent()
            assertEquals(7, store.state.value.groupId)
            assertTrue(store.state.value.canSave)
            runCurrent()
            groups.values.value = emptyList()
            runCurrent()
            assertNull(store.state.value.groupId)
            assertEquals("Draft", store.state.value.name.text.toString())
            today = LocalDate(2026, 10, 29)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(today, store.state.value.today)
        }
        runCurrent()
        assertEquals(0, groups.subscriptions)
    }

}
