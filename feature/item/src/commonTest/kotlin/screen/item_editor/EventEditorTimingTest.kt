package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.item.api.ItemEditorRoute
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.feature.item.testing.FakeEventRepository
import com.mkulikov.taskb.feature.item.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.item.testing.FakeTaskRepository
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorTimingTest {
    private val date = LocalDate(year = 2026, month = 11, day = 6)

    private val ItemEditorStore.eventSchedule: EventSchedule
        get() = state.value.schedule.details as EventSchedule

    private fun endDate(store: ItemEditorStore, date: LocalDate) {
        store.accept(intent = ItemEditorIntent.ChooseEventEndDate)
        store.accept(intent = ItemEditorIntent.EventEndDateChanged(date = date))
    }

    @Test
    fun `new events always have a date and a matching inclusive end date`(): TestResult {
        return runTest {
            val repo = FakeEventRepository()
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = FakeTaskRepository(),
                    events = repo,
                    groups = FakeGroupRepository(),
                    today = { date },
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Event),
                )
                .use { store ->
                    runCurrent()
                    assertEquals(expected = date, actual = store.state.value.schedule.dueDate)
                    assertEquals(expected = date, actual = store.eventSchedule.endDate)
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Event")
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = date, actual = repo.lastWrite?.dueDate)
                    assertEquals(expected = date, actual = repo.lastWrite?.endDate)
                    assertNull(actual = repo.lastWrite?.startTime)
                    assertNull(actual = repo.lastWrite?.durationMinutes)
                }
        }
    }

    @Test
    fun `choosing time supplies an end thirty minutes later and midnight advances the end date`(): TestResult {
        return runTest {
            val repo = FakeEventRepository()
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = FakeTaskRepository(),
                    events = repo,
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Event, initialDate = date),
                )
                .use { store ->
                    runCurrent()
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Late call")
                    store.accept(intent = ItemEditorIntent.StartTimeChanged(time = LocalTime(hour = 23, minute = 50)))
                    runCurrent()
                    assertEquals(
                        expected = LocalDate(year = 2026, month = 11, day = 7),
                        actual = store.eventSchedule.endDate,
                    )
                    assertEquals(expected = LocalTime(hour = 0, minute = 20), actual = store.eventSchedule.endTime)
                    assertEquals(expected = 30, actual = store.eventSchedule.editedDurationMinutes)
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = 30, actual = repo.lastWrite?.durationMinutes)
                }
        }
    }

    @Test
    fun `quick intervals set the actual endpoint and changing start time preserves the interval`(): TestResult {
        return runTest {
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = FakeTaskRepository(),
                    events = FakeEventRepository(),
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Event, initialDate = date),
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = ItemEditorIntent.StartTimeChanged(time = LocalTime(hour = 12, minute = 0)))
                    store.accept(intent = ItemEditorIntent.DurationChanged(minutes = 15))
                    runCurrent()
                    assertEquals(expected = LocalTime(hour = 12, minute = 15), actual = store.eventSchedule.endTime)
                    store.accept(intent = ItemEditorIntent.StartTimeChanged(time = LocalTime(hour = 18, minute = 0)))
                    runCurrent()
                    assertEquals(expected = LocalTime(hour = 18, minute = 15), actual = store.eventSchedule.endTime)
                    store.accept(intent = ItemEditorIntent.DurationChanged(minutes = 60))
                    runCurrent()
                    assertEquals(expected = LocalTime(hour = 19, minute = 0), actual = store.eventSchedule.endTime)
                }
        }
    }

    @Test
    fun `a vacation spans inclusive dates without requiring a time`(): TestResult {
        return runTest {
            val repo = FakeEventRepository()
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = FakeTaskRepository(),
                    events = repo,
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Event, initialDate = date),
                )
                .use { store ->
                    runCurrent()
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Vacation")
                    endDate(store = store, date = LocalDate(year = 2026, month = 11, day = 8))
                    runCurrent()
                    assertTrue(actual = store.state.value.canSave)
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = 3, actual = repo.lastWrite?.durationDays)
                    assertEquals(
                        expected = LocalDate(year = 2026, month = 11, day = 8),
                        actual = repo.lastWrite?.endDate,
                    )
                    assertNull(actual = repo.lastWrite?.startTime)
                    assertNull(actual = repo.lastWrite?.durationMinutes)
                }
        }
    }

    @Test
    fun `changing the start date moves the inclusive date range and clearing time keeps it`(): TestResult {
        return runTest {
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = FakeTaskRepository(),
                    events = FakeEventRepository(),
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Event, initialDate = date),
                )
                .use { store ->
                    runCurrent()
                    endDate(store = store, date = LocalDate(year = 2026, month = 11, day = 8))
                    store.accept(
                        intent = ItemEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 11, day = 10))
                    )
                    store.accept(intent = ItemEditorIntent.StartTimeChanged(time = LocalTime(hour = 9, minute = 0)))
                    runCurrent()
                    assertEquals(
                        expected = LocalDate(year = 2026, month = 11, day = 12),
                        actual = store.eventSchedule.endDate,
                    )
                    assertEquals(expected = LocalTime(hour = 9, minute = 30), actual = store.eventSchedule.endTime)
                    store.accept(intent = ItemEditorIntent.DurationChanged(minutes = 15))
                    runCurrent()
                    assertEquals(
                        expected = LocalDate(year = 2026, month = 11, day = 12),
                        actual = store.eventSchedule.endDate,
                    )
                    assertEquals(expected = LocalTime(hour = 9, minute = 30), actual = store.eventSchedule.endTime)
                    store.accept(intent = ItemEditorIntent.StartTimeChanged(time = null))
                    runCurrent()
                    assertEquals(
                        expected = LocalDate(year = 2026, month = 11, day = 12),
                        actual = store.eventSchedule.endDate,
                    )
                    assertEquals(expected = 3, actual = store.eventSchedule.editedDurationDays)
                    assertNull(actual = store.eventSchedule.endTime)
                }
        }
    }

    @Test
    fun `endpoints before or equal to the start block saving until corrected`(): TestResult {
        return runTest {
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = FakeTaskRepository(),
                    events = FakeEventRepository(),
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Event, initialDate = date),
                )
                .use { store ->
                    runCurrent()
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Invalid until fixed")
                    endDate(store = store, date = LocalDate(year = 2026, month = 11, day = 5))
                    runCurrent()
                    assertFalse(actual = store.state.value.canSave)
                    assertNotNull(actual = store.eventSchedule.timingError)
                    endDate(store = store, date = date)
                    store.accept(intent = ItemEditorIntent.StartTimeChanged(time = LocalTime(hour = 12, minute = 0)))
                    store.accept(intent = ItemEditorIntent.ChooseEndTime)
                    store.accept(intent = ItemEditorIntent.EndTimeChanged(time = LocalTime(hour = 12, minute = 0)))
                    runCurrent()
                    assertFalse(actual = store.state.value.canSave)
                    endDate(store = store, date = LocalDate(year = 2026, month = 11, day = 7))
                    runCurrent()
                    assertTrue(actual = store.state.value.canSave)
                }
        }
    }

    @Test
    fun `existing timed and all day events reopen with matching endpoints and no false dirty state`(): TestResult {
        return runTest {
            for (original in
                listOf(
                    Event(
                        id = 7,
                        name = "Trip",
                        dueDate = date,
                        startTime = LocalTime(hour = 18, minute = 0),
                        durationMinutes = 3840,
                    ),
                    Event(
                        id = 7,
                        name = "Missing duration",
                        dueDate = date,
                        startTime = LocalTime(hour = 15, minute = 0),
                        durationMinutes = null,
                    ),
                    Event(id = 7, name = "Vacation", dueDate = date, durationDays = 3),
                )) {
                val repo = FakeEventRepository().apply { values.value = listOf(element = original) }
                ItemEditorStore(
                        lastSelectedGroupId = { null },
                        tasks = FakeTaskRepository(),
                        events = repo,
                        groups = FakeGroupRepository(),
                        dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                        route = ItemEditorRoute(type = ItemType.Event, itemId = 7),
                    )
                    .use { store ->
                        runCurrent()
                        assertFalse(actual = store.state.value.canSave, message = original.name)
                        assertEquals(expected = original.endDate, actual = store.eventSchedule.endDate)
                        assertEquals(
                            expected = if (original.startTime == null) null else original.durationMinutes ?: 30,
                            actual = store.eventSchedule.editedDurationMinutes,
                        )
                    }
            }
        }
    }

    @Test
    fun `legacy undated events receive a date in the editor`(): TestResult {
        return runTest {
            val repo = FakeEventRepository().apply {
                values.value = listOf(element = Event(id = 7, name = "Unscheduled"))
            }
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = FakeTaskRepository(),
                    events = repo,
                    groups = FakeGroupRepository(),
                    today = { date },
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Event, itemId = 7),
                )
                .use { store ->
                    runCurrent()
                    assertEquals(expected = date, actual = store.state.value.schedule.dueDate)
                    assertEquals(expected = date, actual = store.eventSchedule.endDate)
                    assertTrue(actual = store.state.value.canSave)
                }
        }
    }

    @Test
    fun `changing one recurring occurrence date range asks which occurrences to edit`(): TestResult {
        return runTest {
            val key = ItemOccurrenceId(scheduleId = 1, scheduledDate = date)
            val original = Event(
                id = 7,
                name = "Vacation",
                dueDate = date,
                recurrence = ItemRecurrence.monthly(date = date),
                occurrenceId = key,
            )
            val repo = FakeEventRepository().apply { values.value = listOf(element = original) }
            ItemEditorStore(
                    lastSelectedGroupId = { null },
                    tasks = FakeTaskRepository(),
                    events = repo,
                    groups = FakeGroupRepository(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    route = ItemEditorRoute(type = ItemType.Event, itemId = 7, occurrenceId = key),
                )
                .use { store ->
                    runCurrent()
                    endDate(store = store, date = LocalDate(year = 2026, month = 11, day = 8))
                    store.accept(intent = ItemEditorIntent.Save)
                    runCurrent()
                    assertEquals(expected = ItemEditorDialog.MoveOccurrence, actual = store.state.value.dialog)
                    assertEquals(expected = 0, actual = repo.writes)
                    store.accept(intent = ItemEditorIntent.SaveOccurrence(scope = ItemChangeScope.ThisOccurrence))
                    runCurrent()
                    assertEquals(expected = 3, actual = repo.lastWrite?.durationDays)
                    assertEquals(expected = ItemChangeScope.ThisOccurrence, actual = repo.lastScope)
                }
        }
    }
}
