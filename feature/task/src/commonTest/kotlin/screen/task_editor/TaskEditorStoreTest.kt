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
    fun `group selection callback only receives accepted explicit choices including clearing`() = runTest {
        val selected = mutableListOf<Int?>()
        val gate = CompletableDeferred<Unit>()
        val repo = FakeTaskRepository().apply { onWrite = { gate.await() } }
        TaskEditorStore(repo, groups(), initialGroup = 7, dispatcher = StandardTestDispatcher(testScheduler),
            onGroupSelected = selected::add).use { store ->
            runCurrent()
            assertTrue(selected.isEmpty())
            store.accept(TaskEditorIntent.GroupChanged(999))
            runCurrent()
            assertTrue(selected.isEmpty())
            store.accept(TaskEditorIntent.GroupChanged(null))
            store.accept(TaskEditorIntent.GroupChanged(7))
            runCurrent()
            assertEquals(listOf(null, 7), selected)
            assertEquals(7, store.state.value.groupId)
            store.state.value.name.setTextAndPlaceCursorAtEnd("Task")
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            store.accept(TaskEditorIntent.GroupChanged(null))
            runCurrent()
            assertEquals(listOf(null, 7), selected)
            gate.complete(Unit)
            runCurrent()
            store.accept(TaskEditorIntent.GroupChanged(null))
            runCurrent()
            assertEquals(listOf(null, 7), selected)
        }
    }

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
            val description = "  First line\n\nSecond line with trailing spaces  \n"
            store.state.value.description.setTextAndPlaceCursorAtEnd(description)
            store.accept(TaskEditorIntent.DateChanged(LocalDate(2026, 2, 28)))
            store.accept(TaskEditorIntent.GroupChanged(7))
            repeat(3) { store.accept(TaskEditorIntent.Save) }
            runCurrent()
            assertEquals(1, repo.writes)
            assertEquals(" Task ", store.state.value.name.text.toString())
            gate.complete(Unit)
            runCurrent()
            val task = assertNotNull(repo.lastWrite)
            assertEquals("Task", task.name)
            assertEquals(description, task.description)
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
            store.state.value.description.setTextAndPlaceCursorAtEnd("Draft description\nKeep this text.")
            repo.fail = true
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertIs<TaskEditorDialog.Error>(store.state.value.dialog)
            assertEquals("Draft", store.state.value.name.text.toString())
            assertEquals("Draft description\nKeep this text.", store.state.value.description.text.toString())
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
    fun `editing only description enables save and preserves multiline content through other edits`() = runTest {
        val originalDescription = "Original description\n\n  Preserve whitespace  \n"
        val repo = FakeTaskRepository().apply {
            values.value = listOf(Task(7, "Original", description = originalDescription))
        }
        TaskEditorStore(repo, groups(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(originalDescription, store.state.value.description.text.toString())
            assertFalse(store.state.value.canSave)

            val editedDescription = "  Updated description\n\nKeep a trailing newline.\n"
            val input = store.state.value.description
            input.setTextAndPlaceCursorAtEnd(editedDescription)
            assertTrue(store.state.value.canSave)
            store.accept(TaskEditorIntent.DateChanged(LocalDate(2026, 11, 1)))
            store.accept(TaskEditorIntent.GroupChanged(7))
            runCurrent()
            assertSame(input, store.state.value.description)
            assertEquals(editedDescription, store.state.value.description.text.toString())

            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertEquals(editedDescription, repo.values.value.single().description)
            assertTrue(store.state.value.isFinished)
        }
    }
    @Test
    fun `existing description can be cleared without changing the task name`() = runTest {
        val repo = FakeTaskRepository().apply {
            values.value = listOf(Task(7, "Original", description = "Old description"))
        }
        TaskEditorStore(repo, groups(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.description.setTextAndPlaceCursorAtEnd("")
            assertTrue(store.state.value.canSave)
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            val saved = assertNotNull(repo.lastWrite)
            assertEquals("Original", saved.name)
            assertEquals("", saved.description)
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
