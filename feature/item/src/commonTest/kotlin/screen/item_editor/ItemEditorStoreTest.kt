package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.item.api.ItemEditorRoute
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.testing.FakeEventRepository
import com.mkulikov.taskb.feature.item.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.item.testing.FakeTaskRepository
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ItemEditorStoreTest {
    private fun groups(): FakeGroupRepository {
        return FakeGroupRepository().apply {
            values.value = listOf(element = Group(id = 7, name = "Work", color = GroupColor.Blue))
        }
    }

    @Test
    fun `switching type shares the text and group and restores each schedule before saving`(): TestResult {
        return runTest {
            val tasks = FakeTaskRepository()
            val events = FakeEventRepository()
            val groups = groups()
            val date = LocalDate(year = 2026, month = 11, day = 6)
            val time = LocalTime(hour = 16, minute = 30)
            val rule = ItemRecurrence.monthly(date = date)
            val store = ItemEditorStore(
                lastSelectedGroupId = { 7 },
                tasks = tasks,
                events = events,
                groups = groups,
                route = ItemEditorRoute(),
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            )
            store.use { editor ->
                runCurrent()
                assertEquals(expected = 7, actual = editor.state.value.groupId)
                val title = editor.state.value.name
                title.setTextAndPlaceCursorAtEnd(text = "Planning")
                editor.accept(intent = ItemEditorIntent.DateChanged(date = date))
                editor.accept(intent = ItemEditorIntent.RecurrenceChanged(recurrence = rule))
                editor.accept(intent = ItemEditorIntent.QuickDeleteChanged(enabled = true))
                editor.accept(intent = ItemEditorIntent.SelectType(type = ItemType.Event))
                runCurrent()
                assertEquals(expected = date, actual = editor.state.value.schedule.dueDate)
                assertNull(actual = editor.state.value.schedule.recurrence)
                editor.state.value.description.setTextAndPlaceCursorAtEnd(text = "Bring notes")
                editor.accept(intent = ItemEditorIntent.GroupChanged(id = 7))
                editor.accept(intent = ItemEditorIntent.StartTimeChanged(time = time))
                editor.accept(intent = ItemEditorIntent.SelectType(type = ItemType.Task))
                runCurrent()
                val taskDraft = editor.state.value.schedule
                assertEquals(expected = rule, actual = taskDraft.editedRecurrence)
                assertTrue(actual = (taskDraft.details as TaskSchedule).quickDelete)
                assertEquals(expected = 7, actual = editor.state.value.groupId)
                assertEquals(expected = "Bring notes", actual = editor.state.value.description.text.toString())
                editor.accept(intent = ItemEditorIntent.SelectType(type = ItemType.Event))
                runCurrent()
                assertSame(expected = title, actual = editor.state.value.name)
                assertEquals(expected = 1, actual = groups.subscriptions)
                editor.accept(intent = ItemEditorIntent.Save)
                runCurrent()
                val saved = assertNotNull(actual = events.lastWrite)
                assertEquals(expected = "Planning", actual = saved.name)
                assertEquals(expected = "Bring notes", actual = saved.description)
                assertEquals(expected = 7, actual = saved.groupId)
                assertEquals(expected = time, actual = saved.startTime)
                assertNull(actual = saved.recurrence)
                assertEquals(expected = 0, actual = tasks.writes)
            }
        }
    }

    @Test
    fun `editing an event keeps its type and never changes a task with the same ID`(): TestResult {
        return runTest {
            val date = LocalDate(year = 2026, month = 11, day = 6)
            val task = Task(id = 7, name = "Task")
            val tasks = FakeTaskRepository().apply { values.value = listOf(element = task) }
            val event = Event(id = 7, name = "Meeting", dueDate = date)
            val events = FakeEventRepository().apply { values.value = listOf(element = event) }
            val store = ItemEditorStore(
                lastSelectedGroupId = { error(message = "Editing must keep the item's own group") },
                tasks = tasks,
                events = events,
                groups = groups(),
                route = ItemEditorRoute(type = ItemType.Event, itemId = 7),
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            )
            store.use { editor ->
                runCurrent()
                editor.accept(intent = ItemEditorIntent.SelectType(type = ItemType.Task))
                runCurrent()
                assertEquals(expected = ItemType.Event, actual = editor.state.value.type)
                assertEquals(expected = "Meeting", actual = editor.state.value.name.text.toString())
                editor.state.value.name.setTextAndPlaceCursorAtEnd(text = "Standup")
                editor.accept(intent = ItemEditorIntent.Save)
                runCurrent()
                assertEquals(expected = event.copy(name = "Standup"), actual = events.values.value.single())
                assertEquals(expected = task, actual = tasks.values.value.single())
            }
        }
    }

    @Test
    fun `group selection accepts valid choices and ignores changes during and after saving`(): TestResult {
        return runTest {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeTaskRepository().apply { onWrite = { gate.await() } }
            ItemEditorStore(
                    lastSelectedGroupId = { error(message = "The calendar supplied a group") },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, initialGroupId = 7),
                )
                .use { store ->
                    runCurrent()
                    assertEquals(expected = 7, actual = store.state.value.groupId)
                    store.accept(intent = ItemEditorIntent.GroupChanged(id = 999))
                    runCurrent()
                    assertEquals(expected = 7, actual = store.state.value.groupId)
                    store.accept(intent = ItemEditorIntent.GroupChanged(id = null))
                    runCurrent()
                    assertNull(actual = store.state.value.groupId)
                    store.accept(intent = ItemEditorIntent.GroupChanged(id = 7))
                    runCurrent()
                    assertEquals(expected = 7, actual = store.state.value.groupId)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Task")
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    store.accept(intent = ItemEditorIntent.GroupChanged(id = null))
                    runCurrent()
                    assertEquals(expected = 7, actual = store.state.value.groupId)
                    gate.complete(value = Unit)
                    runCurrent()
                    store.accept(intent = ItemEditorIntent.GroupChanged(id = null))
                    runCurrent()
                    assertEquals(expected = 7, actual = store.state.value.groupId)
                }
        }
    }

    @Test
    fun `remembered group must exist and the no group filter overrides it`(): TestResult {
        return runTest {
            val repository = groups()
            fun open(route: ItemEditorRoute, lastGroup: Int) = ItemEditorStore(
                tasks = FakeTaskRepository(),
                events = FakeEventRepository(),
                groups = repository,
                route = route,
                lastSelectedGroupId = { lastGroup },
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            )

            open(route = ItemEditorRoute(type = ItemType.Event), lastGroup = 7).use { editor ->
                runCurrent()
                assertEquals(expected = 7, actual = editor.state.value.groupId)
                editor.accept(intent = ItemEditorIntent.GroupChanged(id = null))
                runCurrent()
                repository.values.value = repository.values.value.map { group -> group.copy(name = "Renamed") }
                runCurrent()
                assertNull(actual = editor.state.value.groupId)
            }
            open(route = ItemEditorRoute(useLastSelectedGroup = false), lastGroup = 7).use { editor ->
                runCurrent()
                assertNull(actual = editor.state.value.groupId)
            }
            open(route = ItemEditorRoute(), lastGroup = 99).use { editor ->
                runCurrent()
                assertNull(actual = editor.state.value.groupId)
            }
        }
    }

    @Test
    fun `creation validates trims dates and group while preventing duplicate writes`(): TestResult {
        return runTest {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeTaskRepository().apply { onWrite = { gate.await() } }
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task),
                )
                .use { store ->
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "  ")
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = 0, actual = repo.writes)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = " Task ")
                    val description = "  First line\n\nSecond line with trailing spaces  \n"
                    store.state.value.description.setTextAndPlaceCursorAtEnd(text = description)
                    store.accept(
                        intent = ItemEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 2, day = 28))
                    )
                    store.accept(intent = ItemEditorIntent.GroupChanged(id = 7))
                    repeat(times = 3) { store.accept(intent = ItemEditorIntent.Save) }
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
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, itemId = 7),
                )
                .use { store ->
                    runCurrent()
                    assertFalse(actual = store.state.value.canSave)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Draft")
                    store.state.value.description.setTextAndPlaceCursorAtEnd(
                        text = "Draft description\nKeep this text."
                    )
                    repo.fail = true
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertIs<ItemEditorDialog.Error>(value = store.state.value.dialog)
                    assertEquals(expected = "Draft", actual = store.state.value.name.text.toString())
                    assertEquals(
                        expected = "Draft description\nKeep this text.",
                        actual = store.state.value.description.text.toString(),
                    )
                    store.accept(intent = ItemEditorIntent.DismissDialog)
                    store.accept(intent = ItemEditorIntent.ConfirmDelete)
                    runCurrent()
                    assertEquals(expected = 0, actual = repo.deletions)
                    store.accept(intent = ItemEditorIntent.PrepareDelete)
                    repeat(times = 3) { store.accept(intent = ItemEditorIntent.ConfirmDelete) }
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
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, itemId = 7),
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
                        intent = ItemEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 11, day = 1))
                    )
                    store.accept(intent = ItemEditorIntent.GroupChanged(id = 7))
                    runCurrent()
                    assertSame(expected = input, actual = store.state.value.description)
                    assertEquals(expected = editedDescription, actual = store.state.value.description.text.toString())

                    store.accept(intent = ItemEditorIntent.Save)
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
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, itemId = 7),
                )
                .use { store ->
                    runCurrent()
                    store.state.value.description.setTextAndPlaceCursorAtEnd(text = "")
                    assertTrue(actual = store.state.value.canSave)
                    store.accept(intent = ItemEditorIntent.Save)
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
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, itemId = 7),
                )
                .use { store ->
                    runCurrent()
                    assertNotNull(actual = store.state.value.loadError)
                    assertFalse(actual = store.state.value.canSave)
                    repo.fail = false
                    repo.values.value = listOf(element = Task(id = 7, name = "Recovered"))
                    store.accept(intent = ItemEditorIntent.RetryLoad)
                    runCurrent()
                    assertNull(actual = store.state.value.loadError)
                    assertEquals(expected = "Recovered", actual = store.state.value.name.text.toString())
                }
        }
    }

    @Test
    fun `editor opens with calendar defaults and keeps the edited draft when retrying groups`(): TestResult {
        return runTest {
            val groups = groups().apply { failRead = true }
            val repo = FakeTaskRepository()
            var today = LocalDate(year = 2026, month = 10, day = 28)
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = groups,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                    route = ItemEditorRoute(initialDate = today, initialGroupId = 7, initialName = "Draft from search"),
                )
                .use { store ->
                    runCurrent()
                    assertNotNull(actual = store.state.value.groupsLoadError)
                    assertEquals(expected = "Draft from search", actual = store.state.value.name.text.toString())
                    assertEquals(expected = today, actual = store.state.value.schedule.dueDate)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Draft")
                    assertFalse(actual = store.state.value.canSave)
                    groups.failRead = false
                    store.accept(intent = ItemEditorIntent.RetryGroups)
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
