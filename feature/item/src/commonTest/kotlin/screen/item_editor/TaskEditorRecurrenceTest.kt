package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.item.api.ItemEditorRoute
import com.mkulikov.taskb.feature.item.api.model.*
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.feature.item.testing.FakeEventRepository
import com.mkulikov.taskb.feature.item.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.item.testing.FakeTaskRepository
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorRecurrenceTest {
    private val date = LocalDate(year = 2026, month = 11, day = 20)

    @Test
    fun `repeat input validates blanks and end dates and clearing the task date clears recurrence`(): TestResult {
        return runTest {
            val repo = FakeTaskRepository()
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, initialDate = date),
                )
                .use { store ->
                    runCurrent()
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Hosting")
                    store.accept(
                        intent = ItemEditorIntent.RecurrenceChanged(recurrence = ItemRecurrence.monthly(date = date))
                    )
                    runCurrent()
                    store.state.value.schedule.repeatInterval.setTextAndPlaceCursorAtEnd(text = "6")
                    assertEquals(
                        expected = listOf(
                            date,
                            LocalDate(year = 2027, month = 5, day = 20),
                            LocalDate(year = 2027, month = 11, day = 20),
                        ),
                        actual = store.state.value.schedule.upcomingDates,
                    )
                    store.state.value.schedule.repeatInterval.setTextAndPlaceCursorAtEnd(text = "")
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertFalse(actual = store.state.value.canSave)
                    assertNotNull(actual = store.state.value.schedule.recurrenceError)
                    assertEquals(expected = 0, actual = repo.writes)
                    store.state.value.schedule.repeatInterval.setTextAndPlaceCursorAtEnd(text = "1")
                    store.accept(
                        intent = ItemEditorIntent.RecurrenceChanged(
                            recurrence = ItemRecurrence.monthly(date = date)
                                .copy(endDate = LocalDate(year = 2026, month = 11, day = 19))
                        )
                    )
                    runCurrent()
                    assertFalse(actual = store.state.value.canSave)
                    store.accept(intent = ItemEditorIntent.DateChanged(date = null))
                    runCurrent()
                    assertNull(actual = store.state.value.schedule.recurrence)
                    assertTrue(actual = store.state.value.canSave)
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertNull(actual = repo.lastWrite?.recurrence)
                }
        }
    }

    @Test
    fun `moving and deleting an opened occurrence require a scope choice`(): TestResult {
        return runTest {
            val key = ItemOccurrenceId(scheduleId = 1, scheduledDate = date)
            val repo = FakeTaskRepository().apply {
                values.value = listOf(
                    element = Task(
                        id = 7,
                        name = "Rent",
                        dueDate = date,
                        recurrence = ItemRecurrence.monthly(date = date),
                        occurrenceId = key,
                    )
                )
            }
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, itemId = 7, occurrenceId = key),
                )
                .use { store ->
                    runCurrent()
                    store.accept(
                        intent = ItemEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 11, day = 21))
                    )
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = ItemEditorDialog.MoveOccurrence, actual = store.state.value.dialog)
                    assertEquals(expected = 0, actual = repo.writes)
                    store.accept(intent = ItemEditorIntent.SaveOccurrence(scope = ItemChangeScope.ThisOccurrence))
                    runCurrent()
                    assertEquals(expected = ItemChangeScope.ThisOccurrence, actual = repo.lastScope)
                    assertEquals(expected = key, actual = repo.lastWrite?.occurrenceId)
                    assertTrue(actual = store.state.value.isFinished)
                }
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, itemId = 7, occurrenceId = key),
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = ItemEditorIntent.PrepareDelete)
                    runCurrent()
                    assertEquals(expected = ItemEditorDialog.DeleteOccurrence, actual = store.state.value.dialog)
                    assertEquals(expected = 0, actual = repo.deletions)
                    store.accept(intent = ItemEditorIntent.DeleteOccurrence(scope = ItemChangeScope.ThisAndFollowing))
                    runCurrent()
                    assertEquals(expected = ItemChangeScope.ThisAndFollowing, actual = repo.lastScope)
                    assertEquals(expected = 1, actual = repo.deletions)
                }
        }
    }

    @Test
    fun `editing a recurring occurrence description preserves its identity and schedule`(): TestResult {
        return runTest {
            val key = ItemOccurrenceId(scheduleId = 1, scheduledDate = date)
            val rule = ItemRecurrence.monthly(date = date)
            val repo = FakeTaskRepository().apply {
                values.value = listOf(
                    element = Task(
                        id = 7,
                        name = "Rent",
                        dueDate = date,
                        recurrence = rule,
                        occurrenceId = key,
                        description = "Previous payment details",
                    )
                )
            }
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = repo,
                    events = FakeEventRepository(),
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Task, itemId = 7, occurrenceId = key),
                )
                .use { store ->
                    runCurrent()
                    assertEquals(
                        expected = "Previous payment details",
                        actual = store.state.value.description.text.toString(),
                    )
                    val description = "Payment details\nAccount: 123\n"
                    store.state.value.description.setTextAndPlaceCursorAtEnd(text = description)
                    assertTrue(actual = store.state.value.canSave)
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    val saved = assertNotNull(actual = repo.lastWrite)
                    assertEquals(expected = 7, actual = saved.id)
                    assertEquals(expected = key, actual = saved.occurrenceId)
                    assertEquals(expected = rule, actual = saved.recurrence)
                    assertEquals(expected = date, actual = saved.dueDate)
                    assertEquals(expected = description, actual = saved.description)
                    assertTrue(actual = store.state.value.isFinished)
                }
        }
    }
}
