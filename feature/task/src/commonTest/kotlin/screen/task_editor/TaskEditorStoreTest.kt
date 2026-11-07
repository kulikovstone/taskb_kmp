package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import kotlin.test.*
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorStoreTest {
    private fun groups(): FakeGroupRepository {
        return FakeGroupRepository().apply {
            values.value = listOf(element = Group(id = 7, name = "Work", color = GroupColor.Blue))
        }
    }

    @Test
    fun `group selection callback only receives accepted explicit choices including clearing`(): TestResult {
        return runTest {
            val selected = mutableListOf<Int?>()
            val gate = CompletableDeferred<Unit>()
            val repo = FakeTaskRepository().apply { onWrite = { gate.await() } }
            TaskEditorStore(
                    repository = repo,
                    groups = groups(),
                    initialGroup = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    onGroupSelected = selected::add,
                )
                .use { store ->
                    runCurrent()
                    assertTrue(actual = selected.isEmpty())
                    store.accept(intent = TaskEditorIntent.GroupChanged(id = 999))
                    runCurrent()
                    assertTrue(actual = selected.isEmpty())
                    store.accept(intent = TaskEditorIntent.GroupChanged(id = null))
                    store.accept(intent = TaskEditorIntent.GroupChanged(id = 7))
                    runCurrent()
                    assertEquals(expected = listOf(null, 7), actual = selected)
                    assertEquals(expected = 7, actual = store.state.value.groupId)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Task")
                    store.accept(intent = TaskEditorIntent.Save)
                    runCurrent()
                    store.accept(intent = TaskEditorIntent.GroupChanged(id = null))
                    runCurrent()
                    assertEquals(expected = listOf(null, 7), actual = selected)
                    gate.complete(value = Unit)
                    runCurrent()
                    store.accept(intent = TaskEditorIntent.GroupChanged(id = null))
                    runCurrent()
                    assertEquals(expected = listOf(null, 7), actual = selected)
                }
        }
    }

    @Test
    fun `creation validates trims dates and group while preventing duplicate writes`(): TestResult {
        return runTest {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeTaskRepository().apply { onWrite = { gate.await() } }
            TaskEditorStore(
                    repository = repo,
                    groups = groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "  ")
                    store.accept(intent = TaskEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = 0, actual = repo.writes)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = " Task ")
                    val description = "  First line\n\nSecond line with trailing spaces  \n"
                    store.state.value.description.setTextAndPlaceCursorAtEnd(text = description)
                    store.accept(
                        intent = TaskEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 2, day = 28))
                    )
                    store.accept(intent = TaskEditorIntent.GroupChanged(id = 7))
                    repeat(times = 3) { store.accept(intent = TaskEditorIntent.Save) }
                    runCurrent()
                    assertEquals(expected = 1, actual = repo.writes)
                    assertEquals(expected = " Task ", actual = store.state.value.name.text.toString())
                    gate.complete(value = Unit)
                    runCurrent()
                    val task = assertNotNull(actual = repo.lastWrite)
                    assertEquals(expected = "Task", actual = task.name)
                    assertEquals(expected = description, actual = task.description)
                    assertEquals(expected = LocalDate(year = 2026, month = 2, day = 28), actual = task.dueDate)
                    assertEquals(expected = 7, actual = task.groupId)
                    assertNull(actual = task.completedAt)
                    assertTrue(actual = store.state.value.isFinished)
                }
        }
    }

    @Test
    fun `failed saving preserves draft and deletion needs confirmation`(): TestResult {
        return runTest {
            val repo = FakeTaskRepository().apply {
                values.value = listOf(
                    element = Task(
                        id = 7,
                        name = "Original",
                        completedAt = Instant.parse(input = "2026-10-25T10:00:00Z"),
                    )
                )
            }
            TaskEditorStore(
                    repository = repo,
                    groups = groups(),
                    taskId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    runCurrent()
                    assertFalse(actual = store.state.value.canSave)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Draft")
                    store.state.value.description.setTextAndPlaceCursorAtEnd(
                        text = "Draft description\nKeep this text."
                    )
                    repo.fail = true
                    store.accept(intent = TaskEditorIntent.Save)
                    runCurrent()
                    assertIs<TaskEditorDialog.Error>(value = store.state.value.dialog)
                    assertEquals(expected = "Draft", actual = store.state.value.name.text.toString())
                    assertEquals(
                        expected = "Draft description\nKeep this text.",
                        actual = store.state.value.description.text.toString(),
                    )
                    store.accept(intent = TaskEditorIntent.DismissDialog)
                    store.accept(intent = TaskEditorIntent.ConfirmDelete)
                    runCurrent()
                    assertEquals(expected = 0, actual = repo.deletions)
                    store.accept(intent = TaskEditorIntent.PrepareDelete)
                    repeat(times = 3) { store.accept(intent = TaskEditorIntent.ConfirmDelete) }
                    runCurrent()
                    assertEquals(expected = 1, actual = repo.deletions)
                    assertTrue(actual = store.state.value.isFinished)
                }
        }
    }

    @Test
    fun `editing only description enables save and preserves multiline content through other edits`(): TestResult {
        return runTest {
            val originalDescription = "Original description\n\n  Preserve whitespace  \n"
            val repo = FakeTaskRepository().apply {
                values.value = listOf(element = Task(id = 7, name = "Original", description = originalDescription))
            }
            TaskEditorStore(
                    repository = repo,
                    groups = groups(),
                    taskId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    runCurrent()
                    assertEquals(expected = originalDescription, actual = store.state.value.description.text.toString())
                    assertFalse(actual = store.state.value.canSave)

                    val editedDescription = "  Updated description\n\nKeep a trailing newline.\n"
                    val input = store.state.value.description
                    input.setTextAndPlaceCursorAtEnd(text = editedDescription)
                    assertTrue(actual = store.state.value.canSave)
                    store.accept(
                        intent = TaskEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 11, day = 1))
                    )
                    store.accept(intent = TaskEditorIntent.GroupChanged(id = 7))
                    runCurrent()
                    assertSame(expected = input, actual = store.state.value.description)
                    assertEquals(expected = editedDescription, actual = store.state.value.description.text.toString())

                    store.accept(intent = TaskEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = editedDescription, actual = repo.values.value.single().description)
                    assertTrue(actual = store.state.value.isFinished)
                }
        }
    }

    @Test
    fun `existing description can be cleared without changing the task name`(): TestResult {
        return runTest {
            val repo = FakeTaskRepository().apply {
                values.value = listOf(element = Task(id = 7, name = "Original", description = "Old description"))
            }
            TaskEditorStore(
                    repository = repo,
                    groups = groups(),
                    taskId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    runCurrent()
                    store.state.value.description.setTextAndPlaceCursorAtEnd(text = "")
                    assertTrue(actual = store.state.value.canSave)
                    store.accept(intent = TaskEditorIntent.Save)
                    runCurrent()
                    val saved = assertNotNull(actual = repo.lastWrite)
                    assertEquals(expected = "Original", actual = saved.name)
                    assertEquals(expected = "", actual = saved.description)
                }
        }
    }

    @Test
    fun `missing and failed loads cannot save and failed loads can retry`(): TestResult {
        return runTest {
            val repo = FakeTaskRepository().apply { fail = true }
            TaskEditorStore(
                    repository = repo,
                    groups = groups(),
                    taskId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    runCurrent()
                    assertNotNull(actual = store.state.value.loadError)
                    assertFalse(actual = store.state.value.canSave)
                    repo.fail = false
                    repo.values.value = listOf(element = Task(id = 7, name = "Recovered"))
                    store.accept(intent = TaskEditorIntent.RetryLoad)
                    runCurrent()
                    assertNull(actual = store.state.value.loadError)
                    assertEquals(expected = "Recovered", actual = store.state.value.name.text.toString())
                }
        }
    }

    @Test
    fun `standalone editor retries groups keeps its draft and releases subscriptions`(): TestResult {
        return runTest {
            val groups = groups().apply { failRead = true }
            val repo = FakeTaskRepository()
            var today = LocalDate(year = 2026, month = 10, day = 28)
            TaskEditorStore(
                    repository = repo,
                    groups = groups,
                    initialDate = today,
                    initialGroup = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    assertNotNull(actual = store.state.value.groupsLoadError)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Draft")
                    assertFalse(actual = store.state.value.canSave)
                    groups.failRead = false
                    store.accept(intent = TaskEditorIntent.RetryGroups)
                    runCurrent()
                    assertEquals(expected = 7, actual = store.state.value.groupId)
                    assertTrue(actual = store.state.value.canSave)
                    runCurrent()
                    groups.values.value = emptyList()
                    runCurrent()
                    assertNull(actual = store.state.value.groupId)
                    assertEquals(expected = "Draft", actual = store.state.value.name.text.toString())
                    today = LocalDate(year = 2026, month = 10, day = 29)
                    advanceTimeBy(delayTimeMillis = 60_000)
                    runCurrent()
                    assertEquals(expected = today, actual = store.state.value.today)
                }
            runCurrent()
            assertEquals(expected = 0, actual = groups.subscriptions)
        }
    }
}
