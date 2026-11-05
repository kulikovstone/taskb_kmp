package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.feature.event.testing.FakeEventRepository
import com.mkulikov.taskb.feature.event.testing.FakeGroupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorTimingTest {
    private val date = LocalDate(2026, 11, 6)
    private fun endDate(store: EventEditorStore, date: LocalDate) {
        store.accept(EventEditorIntent.ChooseEventEndDate)
        store.accept(EventEditorIntent.EventEndDateChanged(date))
    }

    @Test fun `new events always have a date and a matching inclusive end date`() = runTest {
        val repo = FakeEventRepository()
        EventEditorStore(repo, FakeGroupRepository(), today = { date }, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(date, store.state.value.dueDate)
            assertEquals(date, store.state.value.endDate)
            store.state.value.name.setTextAndPlaceCursorAtEnd("Event")
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertEquals(date, repo.lastWrite?.dueDate)
            assertEquals(date, repo.lastWrite?.endDate)
            assertNull(repo.lastWrite?.startTime)
            assertNull(repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `choosing time supplies an end thirty minutes later and midnight advances the end date`() = runTest {
        val repo = FakeEventRepository()
        EventEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Late call")
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(23, 50)))
            runCurrent()
            assertEquals(LocalDate(2026, 11, 7), store.state.value.endDate)
            assertEquals(LocalTime(0, 20), store.state.value.endTime)
            assertEquals(30, store.state.value.editedDurationMinutes)
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertEquals(30, repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `quick intervals set the actual endpoint and changing start time preserves the interval`() = runTest {
        EventEditorStore(FakeEventRepository(), FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(12, 0)))
            store.accept(EventEditorIntent.DurationChanged(15))
            runCurrent()
            assertEquals(LocalTime(12, 15), store.state.value.endTime)
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(18, 0)))
            runCurrent()
            assertEquals(LocalTime(18, 15), store.state.value.endTime)
            store.accept(EventEditorIntent.DurationChanged(60))
            runCurrent()
            assertEquals(LocalTime(19, 0), store.state.value.endTime)
        }
    }

    @Test fun `a vacation spans inclusive dates without requiring a time`() = runTest {
        val repo = FakeEventRepository()
        EventEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Vacation")
            endDate(store, LocalDate(2026, 11, 8))
            runCurrent()
            assertTrue(store.state.value.canSave)
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertEquals(3, repo.lastWrite?.durationDays)
            assertEquals(LocalDate(2026, 11, 8), repo.lastWrite?.endDate)
            assertNull(repo.lastWrite?.startTime)
            assertNull(repo.lastWrite?.durationMinutes)
        }
    }

    @Test fun `changing the start date moves the inclusive date range and clearing time keeps it`() = runTest {
        EventEditorStore(FakeEventRepository(), FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            endDate(store, LocalDate(2026, 11, 8))
            store.accept(EventEditorIntent.DateChanged(LocalDate(2026, 11, 10)))
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(9, 0)))
            runCurrent()
            assertEquals(LocalDate(2026, 11, 12), store.state.value.endDate)
            assertEquals(LocalTime(9, 30), store.state.value.endTime)
            store.accept(EventEditorIntent.DurationChanged(15))
            runCurrent()
            assertEquals(LocalDate(2026, 11, 12), store.state.value.endDate)
            assertEquals(LocalTime(9, 30), store.state.value.endTime)
            store.accept(EventEditorIntent.StartTimeChanged(null))
            runCurrent()
            assertEquals(LocalDate(2026, 11, 12), store.state.value.endDate)
            assertEquals(3, store.state.value.editedDurationDays)
            assertNull(store.state.value.endTime)
        }
    }

    @Test fun `endpoints before or equal to the start block saving until corrected`() = runTest {
        EventEditorStore(FakeEventRepository(), FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.state.value.name.setTextAndPlaceCursorAtEnd("Invalid until fixed")
            endDate(store, LocalDate(2026, 11, 5))
            runCurrent()
            assertFalse(store.state.value.canSave)
            assertNotNull(store.state.value.timingError)
            endDate(store, date)
            store.accept(EventEditorIntent.StartTimeChanged(LocalTime(12, 0)))
            store.accept(EventEditorIntent.ChooseEndTime)
            store.accept(EventEditorIntent.EndTimeChanged(LocalTime(12, 0)))
            runCurrent()
            assertFalse(store.state.value.canSave)
            endDate(store, LocalDate(2026, 11, 7))
            runCurrent()
            assertTrue(store.state.value.canSave)
        }
    }

    @Test fun `existing timed and all day events reopen with matching endpoints and no false dirty state`() = runTest {
        for (original in listOf(
            Event(7, "Trip", date, startTime = LocalTime(18, 0), durationMinutes = 3840),
            Event(7, "Legacy hour", date, startTime = LocalTime(15, 0), durationMinutes = null),
            Event(7, "Vacation", date, durationDays = 3),
        )) {
            val repo = FakeEventRepository().apply { values.value = listOf(original) }
            EventEditorStore(repo, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
                runCurrent()
                assertFalse(store.state.value.canSave, original.name)
                assertEquals(original.endDate, store.state.value.endDate)
                assertEquals(if (original.startTime == null) null else original.durationMinutes ?: 60, store.state.value.editedDurationMinutes)
            }
        }
    }

    @Test fun `legacy undated events receive a date in the editor`() = runTest {
        val repo = FakeEventRepository().apply { values.value = listOf(Event(7, "Unscheduled")) }
        EventEditorStore(repo, FakeGroupRepository(), 7, today = { date }, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertEquals(date, store.state.value.dueDate)
            assertEquals(date, store.state.value.endDate)
            assertTrue(store.state.value.canSave)
        }
    }

    @Test fun `changing one recurring occurrence date range asks which occurrences to edit`() = runTest {
        val key = EventOccurrenceId(1, date)
        val original = Event(7, "Vacation", date, recurrence = EventRecurrence.monthly(date), occurrenceId = key)
        val repo = FakeEventRepository().apply { values.value = listOf(original) }
        EventEditorStore(repo, FakeGroupRepository(), 7, occurrenceId = key, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            endDate(store, LocalDate(2026, 11, 8))
            store.accept(EventEditorIntent.Save)
            runCurrent()
            assertEquals(EventEditorDialog.MoveOccurrence, store.state.value.dialog)
            assertEquals(0, repo.writes)
            store.accept(EventEditorIntent.SaveOccurrence(EventChangeScope.ThisOccurrence))
            runCurrent()
            assertEquals(3, repo.lastWrite?.durationDays)
            assertEquals(EventChangeScope.ThisOccurrence, repo.lastScope)
        }
    }
}
