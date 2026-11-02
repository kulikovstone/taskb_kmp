package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorTimingTest {
    private val date = LocalDate(2026, 11, 6)

    @Test fun `choosing a start time supplies the mandatory one hour duration`() = runTest {
        val repo = FakeTaskRepository()
        TaskEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Coffee break")
            assertNull(store.state.value.startTime)
            assertTrue(store.state.value.canSave)
            store.accept(TaskEditorIntent.StartTimeChanged(LocalTime(15, 0)))
            runCurrent()
            assertEquals(60, store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.copy(durationMinutes = null).canSave)
            assertFalse(store.state.value.copy(durationMinutes = 0).canSave)
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            val saved = assertNotNull(repo.lastWrite)
            assertEquals(LocalTime(15, 0), saved.startTime)
            assertEquals(60, saved.durationMinutes)
        }
    }

    @Test fun `clearing time and date remove dependent duration while preserving the rest of the draft`() = runTest {
        TaskEditorStore(FakeTaskRepository(), FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Flight")
            store.accept(TaskEditorIntent.StartTimeChanged(LocalTime(18, 0)))
            store.accept(TaskEditorIntent.ChooseCustomDuration)
            store.accept(TaskEditorIntent.DurationInputChanged(days = "2", hours = "16", minutes = "0"))
            runCurrent()
            assertEquals(3840, store.state.value.editedDurationMinutes)
            store.accept(TaskEditorIntent.StartTimeChanged(null))
            runCurrent()
            assertNull(store.state.value.startTime)
            assertNull(store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.customDuration)
            assertEquals(date, store.state.value.dueDate)
            store.accept(TaskEditorIntent.StartTimeChanged(LocalTime(12, 0)))
            runCurrent()
            assertEquals(60, store.state.value.editedDurationMinutes)
            store.accept(TaskEditorIntent.DurationChanged(30))
            store.accept(TaskEditorIntent.DateChanged(null))
            runCurrent()
            assertNull(store.state.value.startTime)
            assertNull(store.state.value.durationMinutes)
            assertEquals("Flight", store.state.value.normalizedName)
            assertTrue(store.state.value.canSave)
        }
    }

    @Test fun `custom multi day duration calculates its end and is saved`() = runTest {
        val repo = FakeTaskRepository()
        TaskEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Business trip")
            store.accept(TaskEditorIntent.StartTimeChanged(LocalTime(18, 0)))
            store.accept(TaskEditorIntent.ChooseCustomDuration)
            store.accept(TaskEditorIntent.DurationInputChanged(days = "2", hours = "16", minutes = "0"))
            runCurrent()
            assertEquals(LocalDateTime(2026, 11, 9, 10, 0), store.state.value.ending)
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertEquals(3840, repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `exact ending requires an explicit later date rather than inferring overnight`() = runTest {
        val repo = FakeTaskRepository()
        TaskEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Flight")
            store.accept(TaskEditorIntent.StartTimeChanged(LocalTime(18, 0)))
            store.accept(TaskEditorIntent.ChooseCustomDuration)
            store.accept(TaskEditorIntent.DurationModeChanged(TaskDurationMode.EndDateTime))
            store.accept(TaskEditorIntent.ChooseEndTime)
            runCurrent()
            assertEquals(TaskEditorDialog.EndTimePicker, store.state.value.dialog)
            store.accept(TaskEditorIntent.EndTimeChanged(LocalTime(10, 0)))
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertNotNull(store.state.value.timingError)
            assertFalse(store.state.value.canSave)
            assertEquals(0, repo.writes)
            store.accept(TaskEditorIntent.ChooseTaskEndDate)
            runCurrent()
            store.accept(TaskEditorIntent.TaskEndDateChanged(LocalDate(2026, 11, 9)))
            runCurrent()
            assertNull(store.state.value.timingError)
            assertEquals(3840, store.state.value.editedDurationMinutes)
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertEquals(3840, repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `invalid custom numbers cannot save and a preset recovers the draft`() = runTest {
        val repo = FakeTaskRepository()
        TaskEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Workout")
            store.accept(TaskEditorIntent.StartTimeChanged(LocalTime(9, 0)))
            store.accept(TaskEditorIntent.ChooseCustomDuration)
            for (input in listOf("", "24")) {
                store.accept(TaskEditorIntent.DurationInputChanged(hours = input))
                store.accept(TaskEditorIntent.Save)
                runCurrent()
                assertFalse(store.state.value.canSave)
                assertNotNull(store.state.value.timingError)
                assertEquals(0, repo.writes)
            }
            store.accept(TaskEditorIntent.DurationInputChanged(days = "0", hours = "0", minutes = "0"))
            runCurrent()
            assertFalse(store.state.value.canSave)
            store.accept(TaskEditorIntent.DurationChanged(15))
            runCurrent()
            assertTrue(store.state.value.canSave)
            assertNull(store.state.value.timingError)
            assertEquals(15, store.state.value.editedDurationMinutes)
        }
    }

    @Test fun `loading custom timing is unchanged until another duration is selected`() = runTest {
        val original = Task(7, "Business trip", dueDate = date, startTime = LocalTime(18, 0), durationMinutes = 3840)
        val repo = FakeTaskRepository().apply { values.value = listOf(original) }
        TaskEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(LocalTime(18, 0), store.state.value.startTime)
            assertEquals("2", store.state.value.durationDays)
            assertEquals("16", store.state.value.durationHours)
            assertEquals(3840, store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.canSave)
            store.accept(TaskEditorIntent.DurationChanged(15))
            runCurrent()
            assertTrue(store.state.value.canSave)
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertEquals(LocalTime(18, 0), repo.lastWrite?.startTime)
            assertEquals(15, repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `legacy timed task without a duration opens with one hour without a false dirty state`() = runTest {
        val repo = FakeTaskRepository().apply {
            values.value = listOf(Task(7, "Old timed task", dueDate = date, startTime = LocalTime(15, 0), durationMinutes = null))
        }
        TaskEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(60, store.state.value.durationMinutes)
            assertEquals(60, store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.canSave)
            assertFalse(store.state.value.customDuration)
        }
    }

    @Test fun `fifteen minute duration is a preset when reopened`() = runTest {
        val repo = FakeTaskRepository().apply {
            values.value = listOf(Task(7, "Coffee break", dueDate = date, startTime = LocalTime(15, 0), durationMinutes = 15))
        }
        TaskEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(15, store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.customDuration)
            assertFalse(store.state.value.canSave)
        }
    }

    @Test fun `changing only occurrence timing asks which events to edit`() = runTest {
        val key = TaskOccurrenceId(1, date)
        val original = Task(7, "Team sync", dueDate = date, startTime = LocalTime(12, 0), durationMinutes = 60,
            recurrence = TaskRecurrence.monthly(date), occurrenceId = key)
        val repo = FakeTaskRepository().apply { values.value = listOf(original) }
        TaskEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler), occurrenceId = key).use { store ->
            runCurrent()
            assertFalse(store.state.value.canSave)
            store.accept(TaskEditorIntent.StartTimeChanged(LocalTime(13, 0)))
            store.accept(TaskEditorIntent.Save)
            runCurrent()
            assertEquals(TaskEditorDialog.MoveOccurrence, store.state.value.dialog)
            assertEquals(0, repo.writes)
            store.accept(TaskEditorIntent.SaveOccurrence(TaskChangeScope.ThisOccurrence))
            runCurrent()
            assertEquals(LocalTime(13, 0), repo.lastWrite?.startTime)
            assertEquals(60, repo.lastWrite?.durationMinutes)
            assertEquals(key, repo.lastWrite?.occurrenceId)
            assertEquals(TaskChangeScope.ThisOccurrence, repo.lastScope)
        }
    }
}
