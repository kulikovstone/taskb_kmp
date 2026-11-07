package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.task.api.model.*
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
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
            TaskEditorStore(
                    repository = repo,
                    groups = FakeGroupRepository(),
                    initialDate = date,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    runCurrent()
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Hosting")
                    store.accept(
                        intent = TaskEditorIntent.RecurrenceChanged(recurrence = TaskRecurrence.monthly(date = date))
                    )
                    runCurrent()
                    store.state.value.repeatInterval.setTextAndPlaceCursorAtEnd(text = "6")
                    assertEquals(
                        expected = listOf(
                            date,
                            LocalDate(year = 2027, month = 5, day = 20),
                            LocalDate(year = 2027, month = 11, day = 20),
                        ),
                        actual = store.state.value.upcomingDates,
                    )
                    store.state.value.repeatInterval.setTextAndPlaceCursorAtEnd(text = "")
                    store.accept(intent = TaskEditorIntent.Save)
                    runCurrent()
                    assertFalse(actual = store.state.value.canSave)
                    assertNotNull(actual = store.state.value.recurrenceError)
                    assertEquals(expected = 0, actual = repo.writes)
                    store.state.value.repeatInterval.setTextAndPlaceCursorAtEnd(text = "1")
                    store.accept(
                        intent = TaskEditorIntent.RecurrenceChanged(
                            recurrence = TaskRecurrence.monthly(date = date)
                                .copy(endDate = LocalDate(year = 2026, month = 11, day = 19))
                        )
                    )
                    runCurrent()
                    assertFalse(actual = store.state.value.canSave)
                    store.accept(intent = TaskEditorIntent.DateChanged(date = null))
                    runCurrent()
                    assertNull(actual = store.state.value.recurrence)
                    assertTrue(actual = store.state.value.canSave)
                    store.accept(intent = TaskEditorIntent.Save)
                    runCurrent()
                    assertNull(actual = repo.lastWrite?.recurrence)
                }
        }
    }

    @Test
    fun `moving and deleting an opened occurrence require a scope choice`(): TestResult {
        return runTest {
            val key = TaskOccurrenceId(scheduleId = 1, scheduledDate = date)
            val repo = FakeTaskRepository().apply {
                values.value = listOf(
                    element = Task(
                        id = 7,
                        name = "Rent",
                        dueDate = date,
                        recurrence = TaskRecurrence.monthly(date = date),
                        occurrenceId = key,
                    )
                )
            }
            TaskEditorStore(
                    repository = repo,
                    groups = FakeGroupRepository(),
                    taskId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    occurrenceId = key,
                )
                .use { store ->
                    runCurrent()
                    store.accept(
                        intent = TaskEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 11, day = 21))
                    )
                    store.accept(intent = TaskEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = TaskEditorDialog.MoveOccurrence, actual = store.state.value.dialog)
                    assertEquals(expected = 0, actual = repo.writes)
                    store.accept(intent = TaskEditorIntent.SaveOccurrence(scope = TaskChangeScope.ThisOccurrence))
                    runCurrent()
                    assertEquals(expected = TaskChangeScope.ThisOccurrence, actual = repo.lastScope)
                    assertEquals(expected = key, actual = repo.lastWrite?.occurrenceId)
                    assertTrue(actual = store.state.value.isFinished)
                }
            TaskEditorStore(
                    repository = repo,
                    groups = FakeGroupRepository(),
                    taskId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    occurrenceId = key,
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = TaskEditorIntent.PrepareDelete)
                    runCurrent()
                    assertEquals(expected = TaskEditorDialog.DeleteOccurrence, actual = store.state.value.dialog)
                    assertEquals(expected = 0, actual = repo.deletions)
                    store.accept(intent = TaskEditorIntent.DeleteOccurrence(scope = TaskChangeScope.ThisAndFollowing))
                    runCurrent()
                    assertEquals(expected = TaskChangeScope.ThisAndFollowing, actual = repo.lastScope)
                    assertEquals(expected = 1, actual = repo.deletions)
                }
        }
    }

    @Test
    fun `editing a recurring occurrence description preserves its identity and schedule`(): TestResult {
        return runTest {
            val key = TaskOccurrenceId(scheduleId = 1, scheduledDate = date)
            val rule = TaskRecurrence.monthly(date = date)
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
            TaskEditorStore(
                    repository = repo,
                    groups = FakeGroupRepository(),
                    taskId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    occurrenceId = key,
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
                    store.accept(intent = TaskEditorIntent.Save)
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
