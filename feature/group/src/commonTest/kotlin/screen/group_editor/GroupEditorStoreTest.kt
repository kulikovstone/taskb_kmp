package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.model.GroupTaskSummary
import com.mkulikov.taskb.feature.group.api.repository.GroupWriteResult
import com.mkulikov.taskb.feature.group.testing.FakeGroupRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GroupEditorStoreTest {
    @Test
    fun `creation validates trims and prevents double submissions`(): TestResult {
        return runTest {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeGroupRepository().apply { onWrite = { gate.await() } }
            GroupEditorStore(repository = repository, dispatcher = StandardTestDispatcher(scheduler = testScheduler))
                .use { store ->
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "  ")
                    store.accept(intent = GroupEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = 0, actual = repository.writes)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = " Work ")
                    store.accept(intent = GroupEditorIntent.ColorChanged(color = GroupColor.Green))
                    repeat(times = 4) { store.accept(intent = GroupEditorIntent.Save) }
                    runCurrent()
                    assertEquals(expected = 1, actual = repository.writes)
                    assertEquals(expected = " Work ", actual = store.state.value.name.text.toString())
                    gate.complete(value = Unit)
                    advanceUntilIdle()
                    assertEquals(expected = "Work", actual = repository.lastWrite?.name)
                    assertEquals(expected = GroupColor.Green, actual = repository.lastWrite?.color)
                    assertTrue(actual = store.state.value.isFinished)
                }
        }
    }

    @Test
    fun `conflict and failed save preserve edit draft and permit retry`(): TestResult {
        return runTest {
            val repository = FakeGroupRepository()
            GroupEditorStore(
                    repository = repository,
                    groupId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    advanceUntilIdle()
                    assertFalse(actual = store.state.value.canSave)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Personal")
                    repository.result = GroupWriteResult.NameConflict
                    store.accept(intent = GroupEditorIntent.Save)
                    advanceUntilIdle()
                    assertEquals(expected = GroupEditorDialog.NameConflict, actual = store.state.value.dialog)
                    store.accept(intent = GroupEditorIntent.DismissDialog)
                    repository.onWrite = { error(message = "Disk full") }
                    store.accept(intent = GroupEditorIntent.Save)
                    advanceUntilIdle()
                    assertTrue(actual = store.state.value.dialog is GroupEditorDialog.Error)
                    assertEquals(expected = "Personal", actual = store.state.value.name.text.toString())
                    store.accept(intent = GroupEditorIntent.DismissDialog)
                    repository.onWrite = {}
                    repository.result = GroupWriteResult.Saved
                    store.accept(intent = GroupEditorIntent.Save)
                    advanceUntilIdle()
                    assertTrue(actual = store.state.value.isFinished)
                }
        }
    }

    @Test
    fun `deletion requires confirmation and duplicate confirmations delete once for empty and populated groups`():
        TestResult {
        return runTest {
            for (hasTasks in listOf(false, true)) {
                val repository = FakeGroupRepository().apply {
                    if (hasTasks)
                        group = requireNotNull(value = group).copy(tasks = GroupTaskSummary(completed = 1))
                }
                GroupEditorStore(
                        repository = repository,
                        groupId = 7,
                        dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    )
                    .use { store ->
                        advanceUntilIdle()
                        store.accept(intent = GroupEditorIntent.ConfirmDelete)
                        advanceUntilIdle()
                        assertEquals(expected = 0, actual = repository.deletions)
                        store.accept(intent = GroupEditorIntent.PrepareDelete)
                        advanceUntilIdle()
                        assertEquals(expected = GroupEditorDialog.ConfirmDelete, actual = store.state.value.dialog)
                        store.accept(intent = GroupEditorIntent.DismissDialog)
                        store.accept(intent = GroupEditorIntent.ConfirmDelete)
                        advanceUntilIdle()
                        assertEquals(expected = 0, actual = repository.deletions)
                        store.accept(intent = GroupEditorIntent.PrepareDelete)
                        repeat(times = 3) { store.accept(intent = GroupEditorIntent.ConfirmDelete) }
                        advanceUntilIdle()
                        assertEquals(expected = 1, actual = repository.deletions)
                    }
            }
        }
    }

    @Test
    fun `closing cancels writes and missing groups can never be saved`(): TestResult {
        return runTest {
            var cancelled = false
            val repository = FakeGroupRepository().apply {
                onWrite = {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                }
            }
            val store = GroupEditorStore(
                repository = repository,
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            )
            store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Work")
            store.accept(intent = GroupEditorIntent.Save)
            runCurrent()
            store.close()
            advanceUntilIdle()
            assertTrue(actual = cancelled)
            repository.group = null
            GroupEditorStore(
                    repository = repository,
                    groupId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { missing ->
                    advanceUntilIdle()
                    assertFalse(actual = missing.state.value.canSave)
                    assertEquals(expected = "This group no longer exists.", actual = missing.state.value.loadError)
                }
        }
    }
}
