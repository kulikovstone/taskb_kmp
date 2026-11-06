package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.task.api.model.*
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorRecurrenceTest {
    private val date = LocalDate(2026, 11, 20)

    @Test fun `repeat input validates blanks and end dates and clearing the task date clears recurrence`() = runTest {
        val repo = FakeTaskRepository()
        TaskEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Hosting")
            store.accept(TaskEditorIntent.RecurrenceChanged(TaskRecurrence.monthly(date)))
            runCurrent()
            store.state.value.repeatInterval.setTextAndPlaceCursorAtEnd("6")
            assertEquals(listOf(date, LocalDate(2027, 5, 20), LocalDate(2027, 11, 20)), store.state.value.upcomingDates)
            store.state.value.repeatInterval.setTextAndPlaceCursorAtEnd("")
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertFalse(store.state.value.canSave)
            assertNotNull(store.state.value.recurrenceError)
            assertEquals(0, repo.writes)
            store.state.value.repeatInterval.setTextAndPlaceCursorAtEnd("1")
            store.accept(TaskEditorIntent.RecurrenceChanged(TaskRecurrence.monthly(date).copy(endDate = LocalDate(2026, 11, 19))))
            runCurrent()
            assertFalse(store.state.value.canSave)
            store.accept(TaskEditorIntent.DateChanged(null))
            runCurrent()
            assertNull(store.state.value.recurrence)
            assertTrue(store.state.value.canSave)
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertNull(repo.lastWrite?.recurrence)
        }
    }

    @Test fun `moving and deleting an opened occurrence require a scope choice`() = runTest {
        val key = TaskOccurrenceId(1, date)
        val repo = FakeTaskRepository().apply { values.value = listOf(Task(7, "Rent", dueDate = date, recurrence = TaskRecurrence.monthly(date), occurrenceId = key)) }
        TaskEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler), occurrenceId = key).use { store ->
            runCurrent()
            store.accept(TaskEditorIntent.DateChanged(LocalDate(2026, 11, 21)))
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertEquals(TaskEditorDialog.MoveOccurrence, store.state.value.dialog)
            assertEquals(0, repo.writes)
            store.accept(TaskEditorIntent.SaveOccurrence(TaskChangeScope.ThisOccurrence))
            runCurrent()
            assertEquals(TaskChangeScope.ThisOccurrence, repo.lastScope)
            assertEquals(key, repo.lastWrite?.occurrenceId)
            assertTrue(store.state.value.isFinished)
        }
        TaskEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler), occurrenceId = key).use { store ->
            runCurrent()
            store.accept(TaskEditorIntent.PrepareDelete)
            runCurrent()
            assertEquals(TaskEditorDialog.DeleteOccurrence, store.state.value.dialog)
            assertEquals(0, repo.deletions)
            store.accept(TaskEditorIntent.DeleteOccurrence(TaskChangeScope.ThisAndFollowing))
            runCurrent()
            assertEquals(TaskChangeScope.ThisAndFollowing, repo.lastScope)
            assertEquals(1, repo.deletions)
        }
    }

    @Test fun `editing a recurring occurrence description preserves its identity and schedule`() = runTest {
        val key = TaskOccurrenceId(1, date)
        val rule = TaskRecurrence.monthly(date)
        val repo = FakeTaskRepository().apply {
            values.value = listOf(Task(7, "Rent", dueDate = date, recurrence = rule, occurrenceId = key,
                description = "Previous payment details"))
        }
        TaskEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler), occurrenceId = key).use { store ->
            runCurrent()
            assertEquals("Previous payment details", store.state.value.description.text.toString())
            val description = "Payment details\nAccount: 123\n"
            store.state.value.description.setTextAndPlaceCursorAtEnd(description)
            assertTrue(store.state.value.canSave)
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            val saved = assertNotNull(repo.lastWrite)
            assertEquals(7, saved.id)
            assertEquals(key, saved.occurrenceId)
            assertEquals(rule, saved.recurrence)
            assertEquals(date, saved.dueDate)
            assertEquals(description, saved.description)
            assertTrue(store.state.value.isFinished)
        }
    }
}
