package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.repository.GroupWriteResult
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.holiday.api.repository.IHolidayRepository
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherCondition
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarStoreTest {
    private val today = LocalDate(year = 2026, month = 10, day = 30)
    private val created = Instant.parse(input = "2026-10-01T10:00:00Z")

    @Test
    fun `year navigation preserves the displayed month and selected day`(): TestResult {
        return runTest {
            CalendarStore(
                    repository = Tasks(tasks = emptyList()),
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = CalendarIntent.NextMonth)
                    store.accept(intent = CalendarIntent.NextYear)
                    runCurrent()
                    assertEquals(expected = YearMonth(year = 2027, month = 11), actual = store.state.value.month)
                    assertEquals(expected = today, actual = store.state.value.selectedDate)

                    store.accept(intent = CalendarIntent.PreviousYear)
                    runCurrent()
                    assertEquals(expected = YearMonth(year = 2026, month = 11), actual = store.state.value.month)
                    assertEquals(expected = today, actual = store.state.value.selectedDate)
                }
        }
    }

    @Test
    fun `completing a hidden daily routine preserves other occurrences and sidebar history`(): TestResult {
        return runTest {
            val first = Task(
                id = 1,
                name = "Daily routine",
                dueDate = today,
                createdAt = created,
                recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily),
                occurrenceId = TaskOccurrenceId(scheduleId = 4, scheduledDate = today),
                showInCalendar = false,
            )
            val second = first.copy(
                dueDate = LocalDate(year = 2026, month = 10, day = 31),
                occurrenceId = TaskOccurrenceId(
                    scheduleId = 4,
                    scheduledDate = LocalDate(year = 2026, month = 10, day = 31),
                ),
            )
            val repository = Tasks(tasks = listOf(first, second))
            CalendarStore(
                    repository = repository,
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    assertEquals(expected = listOf(element = first), actual = store.state.value.sidebarRecurringTasks)
                    assertTrue(actual = store.state.value.monthTasksByDate.isEmpty())
                    store.accept(
                        intent = CalendarIntent.ToggleCompleted(id = first.id, occurrenceId = first.occurrenceId)
                    )
                    runCurrent()
                    assertTrue(actual = store.state.value.sidebarRecurringTasks.isEmpty())
                    assertEquals(expected = first.key, actual = store.state.value.sidebarCompletedTasks.single().key)
                    assertFalse(actual = repository.values.value.single { task -> task.key == second.key }.isCompleted)
                    assertTrue(actual = store.state.value.monthTasksByDate.isEmpty())
                    store.accept(intent = CalendarIntent.SelectDay(date = second.dueDate!!))
                    runCurrent()
                    assertEquals(expected = listOf(element = second), actual = store.state.value.sidebarRecurringTasks)
                    store.accept(intent = CalendarIntent.SelectDay(date = today))
                    runCurrent()
                    assertTrue(actual = store.state.value.sidebarRecurringTasks.isEmpty())
                    assertEquals(expected = first.key, actual = store.state.value.sidebarCompletedTasks.single().key)
                    store.accept(
                        intent = CalendarIntent.ToggleCompleted(id = first.id, occurrenceId = first.occurrenceId)
                    )
                    runCurrent()
                    assertEquals(expected = listOf(element = first), actual = store.state.value.sidebarRecurringTasks)
                    assertTrue(actual = store.state.value.sidebarCompletedTasks.isEmpty())
                }
        }
    }

    @Test
    fun `sidebar defaults to today and keeps every selected day task with active tasks first`() {
        val state = CalendarState(
            today = today,
            tasks = listOf(
                Task(id = 1, name = "Earlier", dueDate = today, createdAt = created),
                Task(id = 2, name = "Later", dueDate = today, createdAt = created + 1.milliseconds),
                Task(
                    id = 3,
                    name = "Another day",
                    dueDate = LocalDate(year = 2026, month = 10, day = 31),
                    createdAt = created,
                ),
                Task(
                    id = 4,
                    name = "Completed history",
                    dueDate = today,
                    createdAt = created,
                    completedAt = created,
                ),
                Task(id = 5, name = "Unscheduled", createdAt = created),
            ),
        )
        assertEquals(expected = today, actual = state.selectedDate)
        assertEquals(expected = listOf(2, 1, 4), actual = state.sidebarTasks.map(transform = Task::id))
        assertEquals(
            expected = listOf(element = 5),
            actual = reduceCalendar(state = state, message = CalendarMessage.SelectedDate(value = null))
                .sidebarTasks
                .map(transform = Task::id),
        )
        assertEquals(
            expected = listOf(element = 3),
            actual = reduceCalendar(
                    state = state,
                    message = CalendarMessage.SelectedDate(value = LocalDate(year = 2026, month = 10, day = 31)),
                )
                .sidebarTasks
                .map(transform = Task::id),
        )
        assertTrue(
            actual = reduceCalendar(
                    state = state,
                    message = CalendarMessage.SelectedDate(value = LocalDate(year = 2026, month = 11, day = 1)),
                )
                .sidebarTasks
                .isEmpty()
        )
    }

    @Test
    fun `day selection exits unscheduled and today restores the date and month without timer resets`(): TestResult {
        return runTest {
            var date = today
            CalendarStore(
                    repository = Tasks(tasks = emptyList()),
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { date },
                )
                .use { store ->
                    runCurrent()
                    assertEquals(expected = today, actual = store.state.value.selectedDate)
                    store.accept(intent = CalendarIntent.ShowUnscheduled)
                    runCurrent()
                    assertNull(actual = store.state.value.selectedDate)
                    val selected = LocalDate(year = 2026, month = 9, day = 28)
                    store.accept(intent = CalendarIntent.SelectDay(date = selected))
                    store.accept(intent = CalendarIntent.NextMonth)
                    runCurrent()
                    assertEquals(expected = selected, actual = store.state.value.selectedDate)
                    assertEquals(expected = YearMonth(year = 2026, month = 11), actual = store.state.value.month)
                    date = LocalDate(year = 2026, month = 11, day = 1)
                    advanceTimeBy(delayTimeMillis = 60_000)
                    runCurrent()
                    assertEquals(expected = selected, actual = store.state.value.selectedDate)
                    store.accept(intent = CalendarIntent.ShowUnscheduled)
                    store.accept(intent = CalendarIntent.Today)
                    runCurrent()
                    assertEquals(expected = date, actual = store.state.value.selectedDate)
                    assertEquals(expected = YearMonth(year = 2026, month = 11), actual = store.state.value.month)
                }
        }
    }

    @Test
    fun `browsing other months retains selected day occurrences without duplicate unscheduled tasks`(): TestResult {
        return runTest {
            val occurrence = TaskOccurrenceId(scheduleId = 4, scheduledDate = today)
            val later = TaskOccurrenceId(scheduleId = 4, scheduledDate = LocalDate(year = 2026, month = 11, day = 30))
            val first = Task(
                id = 1,
                name = "Repeating task",
                dueDate = today,
                recurrence = TaskRecurrence.monthly(date = today),
                occurrenceId = occurrence,
            )
            val second = first.copy(dueDate = later.scheduledDate, occurrenceId = later)
            val repository = Tasks(tasks = listOf(first, second, Task(id = 2, name = "Unscheduled"))).apply {
                boundedCalendar = true
            }
            CalendarStore(
                    repository = repository,
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = CalendarIntent.NextMonth)
                    store.accept(intent = CalendarIntent.NextMonth)
                    runCurrent()
                    assertEquals(expected = YearMonth(year = 2026, month = 12), actual = store.state.value.month)
                    assertEquals(expected = listOf(element = first), actual = store.state.value.sidebarTasks)
                    assertEquals(
                        expected = setOf(first.key, second.key, "2"),
                        actual = store.state.value.tasks.map(transform = Task::key).toSet(),
                    )
                    assertEquals(expected = 3, actual = store.state.value.tasks.size)
                    assertTrue(actual = (today to today) in repository.calendarRanges)
                    store.accept(intent = CalendarIntent.ToggleCompleted(id = 1, occurrenceId = occurrence))
                    runCurrent()
                    assertTrue(actual = store.state.value.sidebarTasks.single().isCompleted)
                    assertFalse(
                        actual = store.state.value.tasks.first { task ->
                            task.occurrenceId == later
                        }.isCompleted,
                    )
                    store.accept(intent = CalendarIntent.ShowUnscheduled)
                    runCurrent()
                    assertEquals(
                        expected = listOf(element = 2),
                        actual = store.state.value.sidebarTasks.map(transform = Task::id),
                    )
                }
            runCurrent()
            assertEquals(expected = 0, actual = repository.subscriptions)
        }
    }

    @Test
    fun `undated tasks put recent completions last exclude older completions and preserve creation order`() {
        val now = Instant.parse(input = "2026-10-30T10:00:00Z")
        val state = CalendarState(
            today = today,
            now = now,
            tasks = listOf(
                Task(
                    id = 1,
                    name = "Old",
                    createdAt = Instant.fromEpochMilliseconds(epochMilliseconds = 1),
                ),
                Task(id = 2, name = "New", createdAt = created),
                Task(id = 3, name = "Completed", createdAt = created, completedAt = now - 1.hours),
                Task(
                    id = 4,
                    name = "Scheduled completed",
                    dueDate = today,
                    createdAt = created,
                    completedAt = created,
                ),
                Task(id = 5, name = "Newest tie", createdAt = created),
                Task(
                    id = 6,
                    name = "Expired exactly at 24 hours",
                    createdAt = now,
                    completedAt = now - 24.hours,
                ),
                Task(
                    id = 7,
                    name = "Just inside the window",
                    createdAt = created - 1.milliseconds,
                    completedAt = now - 24.hours + 1.milliseconds,
                ),
                Task(id = 8, name = "Older completion", createdAt = now, completedAt = now - 25.hours),
            ),
        )
        assertEquals(expected = listOf(5, 2, 1, 3, 7), actual = state.undatedTasks.map(transform = Task::id))
        assertEquals(
            expected = listOf(element = 4),
            actual = state.tasksByDate.values.flatten().map(transform = Task::id),
        )
    }

    @Test
    fun `recent completions expire while open and reopening or completing tasks updates the deadline`(): TestResult {
        return runTest {
            val start = Instant.parse(input = "2026-10-30T10:00:00Z")
            val clock =
                object : Clock {
                    override fun now() = start + testScheduler.currentTime.milliseconds
                }
            val task = Task(
                id = 1,
                name = "Recently completed",
                createdAt = created,
                completedAt = start - 24.hours + 1_500.milliseconds,
            )
            val active = Task(id = 2, name = "Active", createdAt = created)
            val repository = Tasks(tasks = listOf(task, active), now = clock::now)
            CalendarStore(
                    repository = repository,
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                    clock = clock,
                )
                .use { store ->
                    runCurrent()
                    assertEquals(
                        expected = listOf(2, 1),
                        actual = store.state.value.undatedTasks.map(transform = Task::id),
                    )
                    advanceTimeBy(delayTimeMillis = 1_499)
                    runCurrent()
                    assertEquals(
                        expected = listOf(2, 1),
                        actual = store.state.value.undatedTasks.map(transform = Task::id),
                    )
                    advanceTimeBy(delayTimeMillis = 1)
                    runCurrent()
                    assertEquals(
                        expected = listOf(element = 2),
                        actual = store.state.value.undatedTasks.map(transform = Task::id),
                    )
                    assertEquals(expected = listOf(task, active), actual = repository.values.value)

                    store.accept(intent = CalendarIntent.ToggleCompleted(id = 1))
                    runCurrent()
                    assertEquals(
                        expected = listOf(2, 1),
                        actual = store.state.value.undatedTasks.map(transform = Task::id),
                    )
                    assertNull(actual = store.state.value.nextCompletionExpiry)
                    store.accept(intent = CalendarIntent.ToggleCompleted(id = 2))
                    runCurrent()
                    assertEquals(
                        expected = listOf(1, 2),
                        actual = store.state.value.undatedTasks.map(transform = Task::id),
                    )
                    assertEquals(expected = clock.now() + 24.hours, actual = store.state.value.nextCompletionExpiry)

                    repository.values.value = repository.values.value.map { task ->
                        if (task.id == 2) task.copy(completedAt = clock.now() - 24.hours + 500.milliseconds) else task
                    }
                    runCurrent()
                    advanceTimeBy(delayTimeMillis = 500)
                    runCurrent()
                    assertEquals(
                        expected = listOf(element = 1),
                        actual = store.state.value.undatedTasks.map(transform = Task::id),
                    )
                }
            runCurrent()
            assertEquals(expected = 0, actual = repository.subscriptions)
        }
    }

    @Test
    fun `occurrence move prompts for scope and only the selected event changes or is completed`(): TestResult {
        return runTest {
            val firstKey = TaskOccurrenceId(scheduleId = 4, scheduledDate = today)
            val secondKey = TaskOccurrenceId(
                scheduleId = 4,
                scheduledDate = LocalDate(year = 2026, month = 11, day = 30),
            )
            val first = Task(
                id = 1,
                name = "Shared task",
                dueDate = today,
                recurrence = TaskRecurrence.monthly(date = today),
                occurrenceId = firstKey,
            )
            val second = first.copy(dueDate = secondKey.scheduledDate, occurrenceId = secondKey)
            val repository = Tasks(tasks = listOf(first, second))
            CalendarStore(
                    repository = repository,
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    val target = LocalDate(year = 2026, month = 10, day = 31)
                    store.accept(intent = CalendarIntent.MoveTask(id = 1, date = target, occurrenceId = firstKey))
                    runCurrent()
                    assertNotNull(actual = store.state.value.moveChoice)
                    assertEquals(expected = 0, actual = repository.writes)
                    store.accept(intent = CalendarIntent.ConfirmMove(scope = TaskChangeScope.ThisOccurrence))
                    runCurrent()
                    assertEquals(
                        expected = listOf(first.copy(dueDate = target), second),
                        actual = repository.values.value,
                    )
                    store.accept(intent = CalendarIntent.UndoMove)
                    runCurrent()
                    assertEquals(expected = listOf(first, second), actual = repository.values.value)
                    store.accept(intent = CalendarIntent.ToggleCompleted(id = 1, occurrenceId = secondKey))
                    runCurrent()
                    assertFalse(actual = repository.values.value.first().isCompleted)
                    assertTrue(actual = repository.values.value.last().isCompleted)
                }
        }
    }

    @Test
    fun `grid covers complete Monday first weeks including leap days and adjacent years`() {
        val february = CalendarState(today = today, month = YearMonth(year = 2024, month = 2)).days
        assertEquals(expected = LocalDate(year = 2024, month = 1, day = 29), actual = february.first())
        assertEquals(expected = LocalDate(year = 2024, month = 3, day = 3), actual = february.last())
        assertTrue(actual = LocalDate(year = 2024, month = 2, day = 29) in february)
        assertEquals(
            expected = 28,
            actual = CalendarState(today = today, month = YearMonth(year = 2027, month = 2)).days.size,
        )
        assertEquals(
            expected = 42,
            actual = CalendarState(today = today, month = YearMonth(year = 2026, month = 3)).days.size,
        )
        assertEquals(
            expected = LocalDate(year = 2027, month = 1, day = 3),
            actual = CalendarState(today = today, month = YearMonth(year = 2026, month = 12)).days.last(),
        )
    }

    @Test
    fun `moving an undated task and undoing changes only its date and preserves concurrent edits`(): TestResult {
        return runTest {
            val original = Task(id = 1, name = "Original", groupId = 7, createdAt = created, completedAt = created)
            val repository = Tasks(tasks = listOf(element = original))
            CalendarStore(
                    repository = repository,
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = CalendarIntent.MoveTask(id = 1, date = today))
                    runCurrent()
                    assertEquals(expected = original.copy(dueDate = today), actual = repository.values.value.single())
                    assertEquals(expected = 1, actual = store.state.value.undo?.taskId)
                    repository.values.value = listOf(
                        element = repository.values.value.single().copy(
                            name = "Renamed",
                            groupId = 8,
                            completedAt = null,
                        )
                    )
                    runCurrent()
                    store.accept(intent = CalendarIntent.UndoMove)
                    runCurrent()
                    assertEquals(
                        expected = original.copy(name = "Renamed", groupId = 8, completedAt = null),
                        actual = repository.values.value.single(),
                    )
                    assertNull(actual = store.state.value.undo)
                    assertNull(actual = store.state.value.pendingMove)
                }
            runCurrent()
            assertEquals(expected = 0, actual = repository.subscriptions)
        }
    }

    @Test
    fun `scheduled moves restore the previous date and only the latest move can be undone for three seconds`():
        TestResult {
        return runTest {
            val previous = LocalDate(year = 2026, month = 9, day = 30)
            val repository = Tasks(
                tasks = listOf(Task(id = 1, name = "Scheduled", dueDate = previous), Task(id = 2, name = "Undated"))
            )
            CalendarStore(
                    repository = repository,
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = CalendarIntent.MoveTask(id = 1, date = today))
                    runCurrent()
                    advanceTimeBy(delayTimeMillis = 2_000)
                    store.accept(intent = CalendarIntent.MoveTask(id = 2, date = today))
                    runCurrent()
                    advanceTimeBy(delayTimeMillis = 1_000)
                    runCurrent()
                    assertEquals(expected = 2, actual = store.state.value.undo?.taskId)
                    store.accept(intent = CalendarIntent.UndoMove)
                    runCurrent()
                    assertNull(actual = repository.getById(id = 2)?.dueDate)
                    store.accept(intent = CalendarIntent.MoveTask(id = 1, date = previous))
                    runCurrent()
                    store.accept(intent = CalendarIntent.UndoMove)
                    runCurrent()
                    assertEquals(expected = today, actual = repository.getById(id = 1)?.dueDate)
                    store.accept(intent = CalendarIntent.MoveTask(id = 1, date = previous))
                    runCurrent()
                    advanceTimeBy(delayTimeMillis = 2_999)
                    runCurrent()
                    assertNotNull(actual = store.state.value.undo)
                    advanceTimeBy(delayTimeMillis = 1)
                    runCurrent()
                    assertNull(actual = store.state.value.undo)
                    store.accept(intent = CalendarIntent.UndoMove)
                    runCurrent()
                    assertEquals(expected = previous, actual = repository.getById(id = 1)?.dueDate)
                }
        }
    }

    @Test
    fun `same day drops are ignored duplicate pending moves are rejected and failed writes never offer undo`():
        TestResult {
        return runTest {
            val repository = Tasks(tasks = listOf(element = Task(id = 1, name = "Task", dueDate = today)))
            CalendarStore(
                    repository = repository,
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = CalendarIntent.MoveTask(id = 1, date = today))
                    runCurrent()
                    assertEquals(expected = 0, actual = repository.writes)
                    val gate = CompletableDeferred<Unit>()
                    repository.beforeMove = { gate.await() }
                    store.accept(
                        intent = CalendarIntent.MoveTask(id = 1, date = LocalDate(year = 2026, month = 11, day = 1))
                    )
                    store.accept(
                        intent = CalendarIntent.MoveTask(id = 1, date = LocalDate(year = 2026, month = 11, day = 2))
                    )
                    runCurrent()
                    assertEquals(expected = 1, actual = repository.writes)
                    assertNotNull(actual = store.state.value.pendingMove)
                    repository.fail = true
                    gate.complete(value = Unit)
                    runCurrent()
                    assertNull(actual = store.state.value.undo)
                    assertNotNull(actual = store.state.value.error)
                    assertNull(actual = store.state.value.pendingMove)
                    assertEquals(expected = today, actual = repository.values.value.single().dueDate)
                }
        }
    }

    @Test
    fun `undo does not overwrite a newer date and moving a deleted task fails safely`(): TestResult {
        return runTest {
            val repository = Tasks(tasks = listOf(element = Task(id = 1, name = "Task")))
            CalendarStore(
                    repository = repository,
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                )
                .use { store ->
                    runCurrent()
                    store.accept(intent = CalendarIntent.MoveTask(id = 1, date = today))
                    runCurrent()
                    val newer = LocalDate(year = 2027, month = 1, day = 1)
                    repository.values.value = listOf(element = repository.values.value.single().copy(dueDate = newer))
                    runCurrent()
                    store.accept(intent = CalendarIntent.UndoMove)
                    runCurrent()
                    assertEquals(expected = newer, actual = repository.values.value.single().dueDate)
                    assertNotNull(actual = store.state.value.error)
                    repository.beforeMove = { repository.values.value = emptyList() }
                    store.accept(intent = CalendarIntent.MoveTask(id = 1, date = today))
                    runCurrent()
                    assertTrue(actual = repository.values.value.isEmpty())
                    assertNull(actual = store.state.value.undo)
                    assertNotNull(actual = store.state.value.error)
                }
        }
    }

    @Test
    fun `load retries completion failures month changes and local day refresh preserve navigation state`(): TestResult {
        return runTest {
            var date = today
            val repository = Tasks(tasks = listOf(element = Task(id = 1, name = "Task"))).apply { fail = true }
            val groups = Groups()
            CalendarStore(
                    repository = repository,
                    groups = groups,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { date },
                )
                .use { store ->
                    runCurrent()
                    assertNotNull(actual = store.state.value.loadError)
                    repository.fail = false
                    store.accept(intent = CalendarIntent.RetryLoad)
                    store.accept(intent = CalendarIntent.PreviousMonth)
                    runCurrent()
                    assertNull(actual = store.state.value.loadError)
                    assertEquals(expected = YearMonth(year = 2026, month = 9), actual = store.state.value.month)
                    date = LocalDate(year = 2026, month = 11, day = 1)
                    advanceTimeBy(delayTimeMillis = 60_000)
                    runCurrent()
                    assertEquals(expected = date, actual = store.state.value.today)
                    assertEquals(expected = YearMonth(year = 2026, month = 9), actual = store.state.value.month)
                    store.accept(intent = CalendarIntent.Today)
                    store.accept(intent = CalendarIntent.NextMonth)
                    store.accept(intent = CalendarIntent.ToggleCompleted(id = 1))
                    runCurrent()
                    assertEquals(expected = YearMonth(year = 2026, month = 12), actual = store.state.value.month)
                    assertTrue(actual = repository.values.value.single().isCompleted)
                    assertEquals(
                        expected = listOf(element = 1),
                        actual = store.state.value.undatedTasks.map(transform = Task::id),
                    )
                    repository.fail = true
                    store.accept(intent = CalendarIntent.ToggleCompleted(id = 1))
                    runCurrent()
                    assertNotNull(actual = store.state.value.error)
                    assertTrue(actual = store.state.value.pendingCompletion.isEmpty())
                }
            runCurrent()
            assertEquals(expected = 0, actual = repository.subscriptions)
            assertEquals(expected = 0, actual = groups.subscriptions)
        }
    }

    @Test
    fun `weather failure is isolated from task loading and forecast observation closes with calendar`(): TestResult {
        return runTest {
            val city = WeatherLocation(name = "Paris", latitude = 48.85, longitude = 2.35, timeZone = "Europe/Paris")
            val forecast = WeatherForecast(
                location = city,
                error = "Weather unavailable",
            )
            var subscriptions = 0
            var fail = false
            val repository =
                object : IWeatherRepository {
                    override fun observeForecast(): Flow<WeatherForecast> {
                        return flow {
                            subscriptions++
                            try {
                                if (fail) error(message = "Offline")
                                emit(value = forecast)
                                kotlinx.coroutines.awaitCancellation()
                            } finally {
                                subscriptions--
                            }
                        }
                    }
                }
            CalendarStore(
                    repository = Tasks(tasks = listOf(element = Task(id = 1, name = "Plan trip", dueDate = today))),
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                    weatherRepository = repository,
                )
                .use { store ->
                    runCurrent()
                    assertEquals(expected = forecast, actual = store.state.value.weather)
                    assertFalse(actual = store.state.value.isLoading)
                    assertNull(actual = store.state.value.loadError)
                    assertEquals(expected = "Plan trip", actual = store.state.value.sidebarTasks.single().name)
                    fail = true
                    store.accept(intent = CalendarIntent.RetryWeather)
                    runCurrent()
                    assertNotNull(actual = store.state.value.weather.error)
                    assertNull(actual = store.state.value.loadError)
                    fail = false
                    store.accept(intent = CalendarIntent.RetryWeather)
                    runCurrent()
                    assertEquals(expected = 1, actual = subscriptions)
                }
            runCurrent()
            assertEquals(expected = 0, actual = subscriptions)
        }
    }

    @Test
    fun `weather dates use city time zone and exclude expired or distant forecasts`() {
        val city = WeatherLocation(name = "Tokyo", latitude = 35.68, longitude = 139.69, timeZone = "Asia/Tokyo")
        val first = LocalDate(year = 2026, month = 11, day = 1)
        val days = listOf(
                LocalDate(year = 2026, month = 10, day = 31),
                first,
                LocalDate(year = 2026, month = 11, day = 10),
                LocalDate(year = 2026, month = 11, day = 11),
            )
            .map { date ->
                DailyWeather(
                    date = date,
                    condition = WeatherCondition.Clear,
                    minimumCelsius = 10.0,
                    maximumCelsius = 20.0,
                )
            }
        val state = CalendarState(
            today = today,
            now = Instant.parse(input = "2026-10-31T16:00:00Z"),
            weather = WeatherForecast(location = city, days = days),
        )
        assertEquals(
            expected = setOf(first, LocalDate(year = 2026, month = 11, day = 10)),
            actual = state.weatherByDate.keys,
        )
        assertTrue(actual = state.copy(weather = WeatherForecast()).weatherByDate.isEmpty())
    }

    @Test
    fun `holiday observation loads opened months without prefetching adjacent grid months and closes with calendar`():
        TestResult {
        return runTest {
            val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
            var subscriptions = 0
            val holidays =
                object : IHolidayRepository {
                    override fun observeHolidays(from: LocalDate, through: LocalDate): Flow<HolidayCalendar> {
                        return flow {
                            requests += from to through
                            subscriptions++
                            try {
                                emit(value = HolidayCalendar())
                                kotlinx.coroutines.awaitCancellation()
                            } finally {
                                subscriptions--
                            }
                        }
                    }
                }
            CalendarStore(
                    repository = Tasks(tasks = emptyList()),
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                    holidayRepository = holidays,
                )
                .use { store ->
                    runCurrent()
                    assertEquals(
                        expected = listOf(
                            element = LocalDate(year = 2026, month = 10, day = 1) to
                                LocalDate(year = 2026, month = 10, day = 31)
                        ),
                        actual = requests,
                    )
                    store.accept(intent = CalendarIntent.ShowUnscheduled)
                    store.accept(intent = CalendarIntent.NextMonth)
                    runCurrent()
                    assertEquals(
                        expected = LocalDate(year = 2026, month = 11, day = 1) to
                            LocalDate(year = 2026, month = 11, day = 30),
                        actual = requests.last(),
                    )
                    assertEquals(expected = 1, actual = subscriptions)
                    store.accept(
                        intent = CalendarIntent.SelectDay(date = LocalDate(year = 2026, month = 12, day = 1))
                    )
                    runCurrent()
                    assertEquals(
                        expected = setOf(
                            LocalDate(year = 2026, month = 11, day = 1) to
                                LocalDate(year = 2026, month = 11, day = 30),
                            LocalDate(year = 2026, month = 12, day = 1) to
                                LocalDate(year = 2026, month = 12, day = 31),
                        ),
                        actual = requests.takeLast(n = 2).toSet(),
                    )
                    assertEquals(expected = 2, actual = subscriptions)
                    store.accept(intent = CalendarIntent.ShowUnscheduled)
                    store.accept(intent = CalendarIntent.NextYear)
                    runCurrent()
                    assertEquals(
                        expected = LocalDate(year = 2027, month = 11, day = 1) to
                            LocalDate(year = 2027, month = 11, day = 30),
                        actual = requests.last(),
                    )
                    assertEquals(expected = 1, actual = subscriptions)
                }
            runCurrent()
            assertEquals(expected = 0, actual = subscriptions)
        }
    }

    @Test
    fun `holiday errors do not block tasks and retry can recover the opened month`(): TestResult {
        return runTest {
            var fail = true
            val holiday = PublicHoliday(id = "test", date = today, name = "Public holiday", countryCode = "FR")
            val holidays =
                object : IHolidayRepository {
                    override fun observeHolidays(from: LocalDate, through: LocalDate): Flow<HolidayCalendar> {
                        return flow {
                            if (fail) error(message = "Unavailable")
                            emit(value = HolidayCalendar(holidays = listOf(element = holiday)))
                        }
                    }
                }
            CalendarStore(
                    repository = Tasks(tasks = listOf(element = Task(id = 1, name = "Task", dueDate = today))),
                    groups = Groups(),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    today = { today },
                    holidayRepository = holidays,
                )
                .use { store ->
                    runCurrent()
                    assertFalse(actual = store.state.value.isLoading)
                    assertNull(actual = store.state.value.loadError)
                    assertNotNull(actual = store.state.value.holidayCalendar.error)
                    assertEquals(expected = "Task", actual = store.state.value.sidebarTasks.single().name)
                    fail = false
                    store.accept(intent = CalendarIntent.RetryHolidays)
                    runCurrent()
                    assertNull(actual = store.state.value.holidayCalendar.error)
                    assertEquals(
                        expected = listOf(element = holiday),
                        actual = store.state.value.holidayCalendar.holidays,
                    )
                }
        }
    }

    private class Tasks(tasks: List<Task>, private val now: () -> Instant = Clock.System::now) : ITaskRepository {
        val values = MutableStateFlow(value = tasks)
        var fail = false
        var writes = 0
        var subscriptions = 0
        var boundedCalendar = false
        val calendarRanges = mutableListOf<Pair<LocalDate, LocalDate>>()
        var beforeMove: suspend () -> Unit = {}
        override val tasks = flow {
            if (fail) error(message = "Read failed")
            subscriptions++
            try {
                emitAll(flow = values)
            } finally {
                subscriptions--
            }
        }

        override suspend fun getById(id: Int) = values.value.find { task -> task.id == id }

        override fun observeCalendar(from: LocalDate, through: LocalDate): Flow<List<Task>> {
            calendarRanges.add(element = from to through)
            return tasks.map { list ->
                list.filter { task -> !boundedCalendar || task.dueDate?.let { date -> date in from..through } != false }
            }
        }

        override suspend fun save(task: Task): Boolean = error(message = "Unused")

        override suspend fun delete(id: Int) {
            values.value = values.value.filter { task -> task.id != id }
        }

        override suspend fun setCompleted(id: Int, completed: Boolean) {
            if (fail) error(message = "Write failed")
            values.value =
                values.value.map { task ->
                    if (task.id == id) task.copy(completedAt = if (completed) now() else null) else task
                }
        }

        override suspend fun setOccurrenceCompleted(
            id: Int,
            occurrence: TaskOccurrenceId?,
            completed: Boolean,
        ): Boolean {
            if (occurrence == null) {
                setCompleted(id = id, completed = completed)
                return true
            }
            values.value = values.value.map { task ->
                if (task.id == id && task.occurrenceId == occurrence) {
                    task.copy(completedAt = if (completed) now() else null)
                } else {
                    task
                }
            }
            return true
        }

        override suspend fun moveOccurrence(
            id: Int,
            occurrence: TaskOccurrenceId?,
            expectedDate: LocalDate?,
            date: LocalDate,
            scope: TaskChangeScope,
        ): Boolean {
            if (occurrence == null) return reschedule(id = id, expectedDate = expectedDate, date = date)
            writes++
            if (values.value.none { task ->
                task.id == id && task.occurrenceId == occurrence && task.dueDate == expectedDate
            })
                return false
            values.value = values.value.map { task ->
                if (task.id == id && task.occurrenceId == occurrence) task.copy(dueDate = date) else task
            }
            return true
        }

        override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean {
            writes++
            beforeMove()
            if (fail) error(message = "Write failed")
            if (values.value.none { task -> task.id == id && task.dueDate == expectedDate }) return false
            values.value = values.value.map { task -> if (task.id == id) task.copy(dueDate = date) else task }
            return true
        }
    }

    private class Groups : IGroupRepository {
        var subscriptions = 0
        override val groups = flow<List<Group>> {
            subscriptions++
            try {
                emitAll(flow = MutableStateFlow(value = emptyList<Group>()))
            } finally {
                subscriptions--
            }
        }

        override suspend fun getById(id: Int): Group? = null

        override suspend fun add(name: String, color: GroupColor): GroupWriteResult = error(message = "Unused")

        override suspend fun update(group: Group): GroupWriteResult = error(message = "Unused")

        override suspend fun delete(id: Int) = Unit
    }
}
