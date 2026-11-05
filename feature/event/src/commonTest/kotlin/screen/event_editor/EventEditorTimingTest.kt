package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.feature.event.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.event.testing.FakeEventRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorTimingTest {
    private val date = LocalDate(2026, 11, 6)

    @Test fun `choosing a start time supplies the mandatory one hour duration`() = runTest {
        val repo = FakeEventRepository()
        EventEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Coffee break")
            assertNull(store.state.value.startTime)
            assertTrue(store.state.value.canSave)
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(15, 0)))
            runCurrent()
            assertEquals(60, store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.copy(durationMinutes = null).canSave)
            assertFalse(store.state.value.copy(durationMinutes = 0).canSave)
            store.accept(EventEditorIntent.Save)
            runCurrent()
            val saved = assertNotNull(repo.lastWrite)
            assertEquals(LocalTime(15, 0), saved.startTime)
            assertEquals(60, saved.durationMinutes)
        }
    }

    @Test fun `clearing time and date remove dependent duration while preserving the rest of the draft`() = runTest {
        EventEditorStore(FakeEventRepository(), FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Flight")
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(18, 0)))
            store.accept(EventEditorIntent.ChooseCustomDuration)
            store.accept(EventEditorIntent.DurationInputChanged(days = "2", hours = "16", minutes = "0"))
            runCurrent()
            assertEquals(3840, store.state.value.editedDurationMinutes)
            store.accept(EventEditorIntent.StartTimeChanged(null))
            runCurrent()
            assertNull(store.state.value.startTime)
            assertNull(store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.customDuration)
            assertEquals(date, store.state.value.dueDate)
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(12, 0)))
            runCurrent()
            assertEquals(60, store.state.value.editedDurationMinutes)
            store.accept(EventEditorIntent.DurationChanged(30))
            store.accept(EventEditorIntent.DateChanged(null))
            runCurrent()
            assertNull(store.state.value.startTime)
            assertNull(store.state.value.durationMinutes)
            assertEquals("Flight", store.state.value.normalizedName)
            assertTrue(store.state.value.canSave)
        }
    }

    @Test fun `custom multi day duration calculates its end and is saved`() = runTest {
        val repo = FakeEventRepository()
        EventEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Business trip")
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(18, 0)))
            store.accept(EventEditorIntent.ChooseCustomDuration)
            store.accept(EventEditorIntent.DurationInputChanged(days = "2", hours = "16", minutes = "0"))
            runCurrent()
            assertEquals(LocalDateTime(2026, 11, 9, 10, 0), store.state.value.ending)
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertEquals(3840, repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `exact ending requires an explicit later date rather than inferring overnight`() = runTest {
        val repo = FakeEventRepository()
        EventEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Flight")
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(18, 0)))
            store.accept(EventEditorIntent.ChooseCustomDuration)
            store.accept(EventEditorIntent.DurationModeChanged(EventDurationMode.EndDateTime))
            store.accept(EventEditorIntent.ChooseEndTime)
            runCurrent()
            assertEquals(EventEditorDialog.EndTimePicker, store.state.value.dialog)
            store.accept(EventEditorIntent.EndTimeChanged(LocalTime(10, 0)))
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertNotNull(store.state.value.timingError)
            assertFalse(store.state.value.canSave)
            assertEquals(0, repo.writes)
            store.accept(EventEditorIntent.ChooseEventEndDate)
            runCurrent()
            store.accept(EventEditorIntent.EventEndDateChanged(LocalDate(2026, 11, 9)))
            runCurrent()
            assertNull(store.state.value.timingError)
            assertEquals(3840, store.state.value.editedDurationMinutes)
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertEquals(3840, repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `invalid custom numbers cannot save and a preset recovers the draft`() = runTest {
        val repo = FakeEventRepository()
        EventEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Workout")
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(9, 0)))
            store.accept(EventEditorIntent.ChooseCustomDuration)
            for (input in listOf("", "24")) {
                store.accept(EventEditorIntent.DurationInputChanged(hours = input))
                store.accept(EventEditorIntent.Save)
                runCurrent()
                assertFalse(store.state.value.canSave)
                assertNotNull(store.state.value.timingError)
                assertEquals(0, repo.writes)
            }
            store.accept(EventEditorIntent.DurationInputChanged(days = "0", hours = "0", minutes = "0"))
            runCurrent()
            assertFalse(store.state.value.canSave)
            store.accept(EventEditorIntent.DurationChanged(15))
            runCurrent()
            assertTrue(store.state.value.canSave)
            assertNull(store.state.value.timingError)
            assertEquals(15, store.state.value.editedDurationMinutes)
        }
    }

    @Test fun `loading custom timing is unchanged until another duration is selected`() = runTest {
        val original = Event(7, "Business trip", dueDate = date, startTime = LocalTime(18, 0), durationMinutes = 3840)
        val repo = FakeEventRepository().apply { values.value = listOf(original) }
        EventEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(LocalTime(18, 0), store.state.value.startTime)
            assertEquals("2", store.state.value.durationDays)
            assertEquals("16", store.state.value.durationHours)
            assertEquals(3840, store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.canSave)
            store.accept(EventEditorIntent.DurationChanged(15))
            runCurrent()
            assertTrue(store.state.value.canSave)
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertEquals(LocalTime(18, 0), repo.lastWrite?.startTime)
            assertEquals(15, repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `legacy timed event without a duration opens with one hour without a false dirty state`() = runTest {
        val repo = FakeEventRepository().apply {
            values.value = listOf(Event(7, "Old timed event", dueDate = date, startTime = LocalTime(15, 0), durationMinutes = null))
        }
        EventEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(60, store.state.value.durationMinutes)
            assertEquals(60, store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.canSave)
            assertFalse(store.state.value.customDuration)
        }
    }

    @Test fun `fifteen minute duration is a preset when reopened`() = runTest {
        val repo = FakeEventRepository().apply {
            values.value = listOf(Event(7, "Coffee break", dueDate = date, startTime = LocalTime(15, 0), durationMinutes = 15))
        }
        EventEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(15, store.state.value.editedDurationMinutes)
            assertFalse(store.state.value.customDuration)
            assertFalse(store.state.value.canSave)
        }
    }

    @Test fun `changing only occurrence timing asks which events to edit`() = runTest {
        val key = EventOccurrenceId(1, date)
        val original = Event(7, "Team sync", dueDate = date, startTime = LocalTime(12, 0), durationMinutes = 60,
            recurrence = EventRecurrence.monthly(date), occurrenceId = key)
        val repo = FakeEventRepository().apply { values.value = listOf(original) }
        EventEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler), occurrenceId = key).use { store ->
            runCurrent()
            assertFalse(store.state.value.canSave)
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(13, 0)))
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertEquals(EventEditorDialog.MoveOccurrence, store.state.value.dialog)
            assertEquals(0, repo.writes)
            store.accept(EventEditorIntent.SaveOccurrence(EventChangeScope.ThisOccurrence))
            runCurrent()
            assertEquals(LocalTime(13, 0), repo.lastWrite?.startTime)
            assertEquals(60, repo.lastWrite?.durationMinutes)
            assertEquals(key, repo.lastWrite?.occurrenceId)
            assertEquals(EventChangeScope.ThisOccurrence, repo.lastScope)
        }
    }
}
