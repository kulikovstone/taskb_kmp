package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.repository.IHolidayRepository
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minusMonth
import kotlinx.datetime.minusYear
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth
import kotlinx.datetime.plusYear
import kotlinx.datetime.yearMonth

@OptIn(ExperimentalCoroutinesApi::class)
internal class CalendarStore(
    private val repository: ITaskRepository,
    private val groups: IGroupRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val today: () -> LocalDate = ::currentDate,
    private val clock: Clock = Clock.System,
    private val eventRepository: IEventRepository? = null,
    private val weatherRepository: IWeatherRepository? = null,
    private val holidayRepository: IHolidayRepository? = null,
) : CoroutineStore<CalendarIntent, CalendarState, CalendarMessage>(
    initialState = CalendarState(today = today(), now = clock.now()),
    reducer = ::reduceCalendar,
    context = dispatcher,
) {
    private var observation: Job? = null
    private var taskListObservation: Job? = null
    private var weatherObservation: Job? = null
    private var holidayObservation: Job? = null
    private var nextMoveToken = 0L

    init {
        accept(intent = CalendarIntent.RetryLoad)
        accept(intent = CalendarIntent.RetryWeather)
        accept(intent = CalendarIntent.RetryHolidays)
        accept(intent = CalendarIntent.RetryTaskList)
        collectEffect(
            messages = flow {
                while (true) {
                    delay(timeMillis = 60_000)
                    emit(value = CalendarMessage.Today(value = today(), now = clock.now()))
                }
            }
        )
        collectEffect(
            messages = state
                .map { state -> state.nextCompletionExpiry }
                .distinctUntilChanged()
                .flatMapLatest { expiresAt ->
                    flow {
                        if (expiresAt != null) {
                            delay(
                                duration = (expiresAt - clock.now()).coerceAtLeast(minimumValue = Duration.ZERO)
                            )
                            emit(value = CalendarMessage.Today(value = today(), now = clock.now()))
                        }
                    }
                }
        )
    }

    override fun handleIntent(intent: CalendarIntent) {
        when (intent) {
            CalendarIntent.RetryTaskList -> observeTaskList()
            CalendarIntent.RetryHolidays -> observeHolidays()
            CalendarIntent.RetryWeather -> {
                weatherObservation?.cancel()
                weatherObservation = weatherRepository?.let { repository ->
                    collectEffect(
                        messages = repository
                            .observeForecast()
                            .map<WeatherForecast, CalendarMessage> { forecast ->
                                CalendarMessage.Weather(value = forecast)
                            }
                            .catch { exception ->
                                if (exception is CancellationException) throw exception
                                emit(
                                    value = CalendarMessage.Weather(
                                        value = WeatherForecast(
                                            error = "Couldn't load weather. Please try again."
                                        )
                                    )
                                )
                            }
                    )
                }
            }
            CalendarIntent.PreviousMonth -> reduce(
                message = CalendarMessage.Month(value = state.value.month.minusMonth()),
            )
            CalendarIntent.NextMonth -> reduce(message = CalendarMessage.Month(value = state.value.month.plusMonth()))
            CalendarIntent.PreviousYear -> reduce(
                message = CalendarMessage.Month(value = state.value.month.minusYear()),
            )
            CalendarIntent.NextYear -> reduce(message = CalendarMessage.Month(value = state.value.month.plusYear()))
            CalendarIntent.Today -> {
                val date = today()
                reduce(message = CalendarMessage.Today(value = date, now = clock.now()))
                reduce(message = CalendarMessage.Month(value = date.yearMonth))
                reduce(message = CalendarMessage.SelectedDate(value = date))
            }
            is CalendarIntent.SelectDay -> reduce(message = CalendarMessage.SelectedDate(value = intent.date))
            CalendarIntent.ShowUnscheduled -> reduce(message = CalendarMessage.SelectedDate(value = null))
            CalendarIntent.DismissError -> reduce(message = CalendarMessage.DismissError)
            CalendarIntent.DismissMoveChoice -> reduce(message = CalendarMessage.MoveChoice(value = null))
            is CalendarIntent.ConfirmMove -> {
                val choice = state.value.moveChoice ?: return
                reduce(message = CalendarMessage.MoveChoice(value = null))
                move(move = choice.copy(scope = intent.scope), undoing = false)
            }
            CalendarIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(message = CalendarMessage.Loading)
                val range = state
                    .map { state ->
                        val days = state.days
                        val from = days.first()
                        val through = days.last()
                        Triple(
                            first = from,
                            second = through,
                            third = state.selectedDate?.takeUnless { date -> date in from..through },
                        )
                    }
                    .distinctUntilChanged()
                val events = range
                    .flatMapLatest { (from, through, selectedDate) ->
                        val calendar = repository.observeCalendar(from = from, through = through)
                        if (selectedDate == null) {
                            calendar
                        } else {
                            combine(
                                flow = calendar,
                                flow2 = repository.observeCalendar(from = selectedDate, through = selectedDate),
                            ) { visibleTasks, selectedTasks ->
                                (visibleTasks + selectedTasks).distinctBy(selector = Task::key)
                            }
                        }
                    }
                val calendarEvents = range
                    .flatMapLatest { (from, through, selectedDate) ->
                        val calendar = eventRepository?.observeCalendar(from = from, through = through)
                            ?: flowOf(value = emptyList())
                        if (selectedDate == null || eventRepository == null) {
                            calendar
                        } else {
                            combine(
                                flow = calendar,
                                flow2 = eventRepository.observeCalendar(
                                    from = selectedDate,
                                    through = selectedDate,
                                ),
                            ) { visible, selected ->
                                (visible + selected).distinctBy(selector = Event::key)
                            }
                        }
                    }
                observation = collectEffect(
                    messages = combine(flow = events, flow2 = groups.groups, flow3 = calendarEvents) {
                            tasks,
                            groups,
                            savedEvents ->
                            CalendarMessage.Loaded(
                                tasks = tasks,
                                groups = groups,
                                now = clock.now(),
                                events = savedEvents,
                            ) as CalendarMessage
                        }
                        .catch { exception ->
                            if (exception is CancellationException) throw exception
                            emit(value = CalendarMessage.LoadFailed)
                        }
                )
            }
            is CalendarIntent.MoveEvent -> {
                val event = state.value.events.find { event ->
                    event.id == intent.id && event.occurrenceId == intent.occurrenceId
                }
                    ?: return
                if (
                    event.dueDate == intent.date ||
                        state.value.pendingMove != null ||
                        state.value.moveChoice != null
                )
                    return
                val request = CalendarMove(
                    token = ++nextMoveToken,
                    taskId = event.id,
                    previousDate = event.dueDate,
                    date = intent.date,
                    isEvent = true,
                    eventOccurrenceId = event.occurrenceId,
                )
                if (event.occurrenceId != null) {
                    reduce(message = CalendarMessage.MoveChoice(value = request))
                } else {
                    move(move = request, undoing = false)
                }
            }
            is CalendarIntent.MoveTask -> {
                val task = findTask(id = intent.id, occurrenceId = intent.occurrenceId) ?: return
                if (
                    task.dueDate == intent.date ||
                        state.value.pendingMove != null ||
                        task.id in state.value.pendingTaskUpdates
                )
                    return
                if (state.value.moveChoice != null) return
                val request = CalendarMove(
                    token = ++nextMoveToken,
                    taskId = task.id,
                    previousDate = task.dueDate,
                    date = intent.date,
                    occurrenceId = task.occurrenceId,
                )
                if (task.occurrenceId != null) {
                    reduce(message = CalendarMessage.MoveChoice(value = request))
                } else {
                    move(move = request, undoing = false)
                }
            }
            CalendarIntent.UndoMove -> {
                val move = state.value.undo ?: return
                if (state.value.pendingMove != null || move.taskId in state.value.pendingTaskUpdates) return
                move(move = move, undoing = true)
            }
            is CalendarIntent.ToggleCompleted -> {
                val task = findTask(id = intent.id, occurrenceId = intent.occurrenceId) ?: return
                if (task.id == state.value.pendingMove || task.id in state.value.pendingTaskUpdates) return
                reduce(message = CalendarMessage.UpdatingTask(id = task.id))
                launchEffect {
                    try {
                        CalendarMessage.Completed(
                            id = task.id,
                            failed =
                                !repository.setOccurrenceCompleted(
                                    id = task.id,
                                    occurrence = task.occurrenceId,
                                    completed = !task.isCompleted,
                                ),
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        CalendarMessage.Completed(id = task.id, failed = true)
                    }
                }
            }
            is CalendarIntent.PrepareQuickDelete -> {
                val current = state.value
                val task = findTask(id = intent.id, occurrenceId = intent.occurrenceId) ?: return
                if (
                    !task.canQuickDelete ||
                        current.pendingMove != null ||
                        current.moveChoice != null ||
                        current.quickDeleteConfirmation != null ||
                        task.id in current.pendingTaskUpdates
                ) return
                reduce(message = CalendarMessage.QuickDeleteConfirmation(task = task))
            }
            CalendarIntent.DismissQuickDelete -> reduce(message = CalendarMessage.QuickDeleteConfirmation(task = null))
            CalendarIntent.ConfirmQuickDelete -> {
                val task = state.value.quickDeleteConfirmation ?: return
                val occurrence = task.occurrenceId ?: return
                val date = task.dueDate ?: return
                reduce(message = CalendarMessage.QuickDeleteConfirmation(task = null))
                reduce(message = CalendarMessage.UpdatingTask(id = task.id))
                launchEffect {
                    val deleted = try {
                        repository.quickDeleteOccurrence(
                            id = task.id,
                            occurrence = occurrence,
                            expectedDate = date,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        false
                    }
                    CalendarMessage.QuickDeleteFinished(
                        id = task.id,
                        occurrenceId = occurrence,
                        failed = !deleted,
                    )
                }
            }
        }
    }

    private fun findTask(id: Int, occurrenceId: TaskOccurrenceId?): Task? {
        val current = state.value
        return (current.tasks.asSequence() + current.taskList.orEmpty()).firstOrNull { task ->
            task.id == id && task.occurrenceId == occurrenceId
        }
    }

    private fun observeTaskList() {
        if (taskListObservation?.isActive == true) return
        reduce(message = CalendarMessage.TaskListLoading)
        taskListObservation = collectEffect(
            messages = state
                .map { state -> state.today }
                .distinctUntilChanged()
                .flatMapLatest(transform = repository::observeTaskList)
                .map<List<Task>, CalendarMessage>(transform = CalendarMessage::TaskListLoaded)
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    emit(value = CalendarMessage.TaskListLoadFailed)
                },
        )
    }

    private fun observeHolidays() {
        val repository = holidayRepository ?: return
        holidayObservation?.cancel()
        holidayObservation = collectEffect(
            messages = state
                .map { value ->
                    value.month to value.selectedDate?.yearMonth?.takeUnless { month -> month == value.month }
                }
                .distinctUntilChanged()
                .flatMapLatest { (month, selectedMonth) ->
                    val visible = repository.observeHolidays(
                        from = month.firstDay,
                        through = month.firstDay.plus(
                            value = month.numberOfDays - 1,
                            unit = DateTimeUnit.DAY,
                        ),
                    )
                    val calendars = if (selectedMonth == null) {
                        visible
                    } else {
                        combine(
                            flow = visible,
                            flow2 = repository.observeHolidays(
                                from = selectedMonth.firstDay,
                                through = selectedMonth.firstDay.plus(
                                    value = selectedMonth.numberOfDays - 1,
                                    unit = DateTimeUnit.DAY,
                                ),
                            ),
                        ) { main, selected ->
                            if (main.location != selected.location) {
                                main.copy(holidays = emptyList(), isLoading = true)
                            } else {
                                main.copy(
                                    holidays = (main.holidays + selected.holidays).distinctBy { holiday -> holiday.id },
                                    isLoading = main.isLoading || selected.isLoading,
                                    error = listOfNotNull(main.error, selected.error)
                                        .distinct()
                                        .joinToString(separator = " ")
                                        .takeIf(predicate = String::isNotBlank),
                                )
                            }
                        }
                    }
                    calendars.map<HolidayCalendar, CalendarMessage> { calendar ->
                        CalendarMessage.Holidays(month = month, selectedMonth = selectedMonth, value = calendar)
                    }
                }
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    val value = state.value
                    emit(
                        value = CalendarMessage.Holidays(
                            month = value.month,
                            selectedMonth = value.selectedDate?.yearMonth?.takeUnless { month ->
                                month == value.month
                            },
                            value = HolidayCalendar(
                                error = "Couldn't load public holidays. Please try again."
                            ),
                        )
                    )
                }
        )
    }

    private fun move(move: CalendarMove, undoing: Boolean) {
        reduce(message = CalendarMessage.Moving(id = move.taskId, undoing = undoing))
        collectEffect(
            messages = flow {
                val error = try {
                    val updated = if (move.isEvent) {
                        val events = requireNotNull(value = eventRepository)
                        if (move.eventOccurrenceId != null) {
                            events.moveOccurrence(
                                id = move.taskId,
                                occurrence = move.eventOccurrenceId,
                                expectedDate = if (undoing) move.date else move.previousDate,
                                date = if (undoing) requireNotNull(value = move.previousDate) else move.date,
                                scope = if (move.scope == TaskChangeScope.ThisOccurrence) {
                                    EventChangeScope.ThisOccurrence
                                } else {
                                    EventChangeScope.ThisAndFollowing
                                },
                            )
                        } else {
                            events.reschedule(
                                id = move.taskId,
                                expectedDate = if (undoing) move.date else move.previousDate,
                                date = if (undoing) move.previousDate else move.date,
                            )
                        }
                    } else if (move.occurrenceId != null) {
                        repository.moveOccurrence(
                            id = move.taskId,
                            occurrence = move.occurrenceId,
                            expectedDate = if (undoing) move.date else move.previousDate,
                            date = if (undoing) requireNotNull(value = move.previousDate) else move.date,
                            scope = move.scope,
                        )
                    } else {
                        repository.reschedule(
                            id = move.taskId,
                            expectedDate = if (undoing) move.date else move.previousDate,
                            date = if (undoing) move.previousDate else move.date,
                        )
                    }
                    if (updated) {
                        null
                    } else if (!move.isEvent && move.scope == TaskChangeScope.ThisAndFollowing) {
                        ("This event changed, was deleted, or overlaps completed history. " +
                            "Reopen it and try again.")
                    } else {
                        ("This ${if (move.isEvent) "event" else "task"} has changed or was " +
                            "deleted. Please try again.")
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    if (undoing) {
                        "Couldn't undo the move. Please try again."
                    } else {
                        "Couldn't move this ${if (move.isEvent) "event" else "task"}. Please try again."
                    }
                }
                emit(value = CalendarMessage.Moved(move = move, undoing = undoing, error = error))
                if (error == null && !undoing) {
                    delay(timeMillis = 3_000)
                    emit(value = CalendarMessage.ExpireUndo(token = move.token))
                }
            }
        )
    }
}
