package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.task.model.Task
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorStoreTest {
    @Test
    fun `creation validates trims dates and group while preventing duplicate writes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeTaskRepository().apply { onWrite = { gate.await() } }
        TaskEditorStore(repo, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
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
            advanceUntilIdle()
            assertEquals(Task(name = "Task", dueDate = LocalDate(2026, 2, 28), groupId = 7), repo.lastWrite)
            assertTrue(store.state.value.isFinished)
        }
    }
    @Test
    fun `failed saving preserves draft and deletion needs confirmation`() = runTest {
        val repo = FakeTaskRepository().apply { values.value = listOf(Task(7, "Original", completed = true)) }
        TaskEditorStore(repo, 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            advanceUntilIdle()
            assertFalse(store.state.value.canSave)
            store.state.value.name.setTextAndPlaceCursorAtEnd("Draft")
            repo.fail = true
            store.accept(TaskEditorIntent.Save)
            advanceUntilIdle()
            assertIs<TaskEditorDialog.Error>(store.state.value.dialog)
            assertEquals("Draft", store.state.value.name.text.toString())
            store.accept(TaskEditorIntent.DismissDialog)
            store.accept(TaskEditorIntent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(0, repo.deletions)
            store.accept(TaskEditorIntent.PrepareDelete)
            repeat(3) { store.accept(TaskEditorIntent.ConfirmDelete) }
            advanceUntilIdle()
            assertEquals(1, repo.deletions)
            assertTrue(store.state.value.isFinished)
        }
    }
    @Test
    fun `missing and failed loads cannot save and failed loads can retry`() = runTest {
        val repo = FakeTaskRepository().apply { fail = true }
        TaskEditorStore(repo, 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            advanceUntilIdle()
            assertNotNull(store.state.value.loadError)
            assertFalse(store.state.value.canSave)
            repo.fail = false
            repo.values.value = listOf(Task(7, "Recovered"))
            store.accept(TaskEditorIntent.RetryLoad)
            advanceUntilIdle()
            assertNull(store.state.value.loadError)
            assertEquals("Recovered", store.state.value.name.text.toString())
        }
    }
}
