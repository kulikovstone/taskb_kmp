package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.Duration
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository

@OptIn(ExperimentalCoroutinesApi::class)
internal class CalendarStore(
    private val repository: ITaskRepository,
    private val groups: IGroupRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val today: () -> LocalDate = ::currentDate,
    private val clock: Clock = Clock.System,
    private val eventRepository: IEventRepository? = null,
    private val weatherRepository: IWeatherRepository? = null,
) : CoroutineStore<CalendarIntent, CalendarState, CalendarMessage>(CalendarState(today(), now = clock.now()), ::reduceCalendar, dispatcher) {
    private var observation: Job? = null
    private var weatherObservation: Job? = null
    private var nextMoveToken = 0L

    init {
        accept(CalendarIntent.RetryLoad)
        accept(CalendarIntent.RetryWeather)
        collectEffect(flow {
            while (true) {
                delay(60_000)
                emit(CalendarMessage.Today(today(), clock.now()))
            }
        })
        collectEffect(state.map { it.nextCompletionExpiry }.distinctUntilChanged().flatMapLatest { expiresAt ->
            flow {
                if (expiresAt != null) {
                    delay((expiresAt - clock.now()).coerceAtLeast(Duration.ZERO))
                    emit(CalendarMessage.Today(today(), clock.now()))
                }
            }
        })
    }

    override fun handleIntent(intent: CalendarIntent) {
        when (intent) {
            CalendarIntent.RetryWeather -> {
                weatherObservation?.cancel()
                weatherObservation = weatherRepository?.let { repository ->
                    collectEffect(repository.observeForecast().map<WeatherForecast, CalendarMessage> { CalendarMessage.Weather(it) }.catch {
                        if (it is CancellationException) throw it
                        emit(CalendarMessage.Weather(WeatherForecast(error = "Couldn't load weather. Please try again.")))
                    })
                }
            }
            CalendarIntent.PreviousMonth -> reduce(CalendarMessage.Month(state.value.month.minusMonth()))
            CalendarIntent.NextMonth -> reduce(CalendarMessage.Month(state.value.month.plusMonth()))
            CalendarIntent.Today -> {
                val date = today()
                reduce(CalendarMessage.Today(date, clock.now()))
                reduce(CalendarMessage.Month(date.yearMonth))
                reduce(CalendarMessage.SelectedDate(date))
            }
            is CalendarIntent.SelectDay -> reduce(CalendarMessage.SelectedDate(intent.date))
            CalendarIntent.ChooseDate -> reduce(CalendarMessage.DatePicker(true))
            CalendarIntent.DismissDatePicker -> reduce(CalendarMessage.DatePicker(false))
            is CalendarIntent.DateChosen -> if (state.value.isDatePickerOpen) reduce(CalendarMessage.DateChosen(intent.date))
            CalendarIntent.ShowUnscheduled -> reduce(CalendarMessage.SelectedDate(null))
            CalendarIntent.DismissError -> reduce(CalendarMessage.DismissError)
            CalendarIntent.DismissMoveChoice -> reduce(CalendarMessage.MoveChoice(null))
            is CalendarIntent.ConfirmMove -> {
                val choice = state.value.moveChoice ?: return
                reduce(CalendarMessage.MoveChoice(null))
                move(choice.copy(scope = intent.scope), undoing = false)
            }
            CalendarIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(CalendarMessage.Loading)
                val events = state.map {
                    val from = it.days.first()
                    val through = it.days.last()
                    Triple(from, through, it.selectedDate?.takeUnless { date -> date in from..through })
                }.distinctUntilChanged().flatMapLatest { (from, through, selectedDate) ->
                    val calendar = repository.observeCalendar(from, through)
                    if (selectedDate == null) calendar else combine(
                        calendar, repository.observeCalendar(selectedDate, selectedDate),
                    ) { visibleTasks, selectedTasks -> (visibleTasks + selectedTasks).distinctBy { it.key } }
                }
                val calendarEvents = state.map {
                    Triple(it.days.first(), it.days.last(), it.selectedDate?.takeUnless { date -> date in it.days.first()..it.days.last() })
                }.distinctUntilChanged().flatMapLatest { (from, through, selectedDate) ->
                    val calendar = eventRepository?.observeCalendar(from, through) ?: flowOf(emptyList())
                    if (selectedDate == null || eventRepository == null) calendar else combine(calendar,
                        eventRepository.observeCalendar(selectedDate, selectedDate)) { visible, selected -> (visible + selected).distinctBy { it.key } }
                }
                observation = collectEffect(combine(events, groups.groups, calendarEvents) { tasks, groups, savedEvents ->
                    CalendarMessage.Loaded(tasks, groups, clock.now(), savedEvents) as CalendarMessage
                }.catch {
                    if (it is CancellationException) throw it
                    emit(CalendarMessage.LoadFailed)
                })
            }
            is CalendarIntent.MoveEvent -> {
                val event = state.value.events.find { it.id == intent.id && it.occurrenceId == intent.occurrenceId } ?: return
                if (event.dueDate == intent.date || state.value.pendingMove != null || state.value.moveChoice != null) return
                val request = CalendarMove(++nextMoveToken, event.id, event.dueDate, intent.date,
                    isEvent = true, eventOccurrenceId = event.occurrenceId)
                if (event.occurrenceId != null) reduce(CalendarMessage.MoveChoice(request)) else move(request, undoing = false)
            }
            is CalendarIntent.MoveTask -> {
                val task = state.value.tasks.find { it.id == intent.id && it.occurrenceId == intent.occurrenceId } ?: return
                if (task.dueDate == intent.date || state.value.pendingMove != null || task.id in state.value.pendingCompletion) return
                if (state.value.moveChoice != null) return
                val request = CalendarMove(++nextMoveToken, task.id, task.dueDate, intent.date, task.occurrenceId)
                if (task.occurrenceId != null) reduce(CalendarMessage.MoveChoice(request)) else move(request, undoing = false)
            }
            CalendarIntent.UndoMove -> {
                val move = state.value.undo ?: return
                if (state.value.pendingMove != null || move.taskId in state.value.pendingCompletion) return
                move(move, undoing = true)
            }
            is CalendarIntent.ToggleCompleted -> {
                val task = state.value.tasks.find { it.id == intent.id && it.occurrenceId == intent.occurrenceId } ?: return
                if (task.id == state.value.pendingMove || task.id in state.value.pendingCompletion) return
                reduce(CalendarMessage.Completing(task.id))
                launchEffect {
                    try {
                        CalendarMessage.Completed(task.id, !repository.setOccurrenceCompleted(task.id, task.occurrenceId, !task.isCompleted))
                    } catch (exception: CancellationException) { throw exception }
                    catch (_: Exception) { CalendarMessage.Completed(task.id, true) }
                }
            }
        }
    }

    private fun move(move: CalendarMove, undoing: Boolean) {
        reduce(CalendarMessage.Moving(move.taskId, undoing))
        collectEffect(flow {
            val error = try {
                val updated = if (move.isEvent) {
                    val events = requireNotNull(eventRepository)
                    if (move.eventOccurrenceId != null) events.moveOccurrence(move.taskId, move.eventOccurrenceId,
                        if (undoing) move.date else move.previousDate,
                        if (undoing) requireNotNull(move.previousDate) else move.date,
                        if (move.scope == TaskChangeScope.ThisOccurrence) EventChangeScope.ThisOccurrence
                        else EventChangeScope.ThisAndFollowing)
                    else events.reschedule(move.taskId, if (undoing) move.date else move.previousDate,
                        if (undoing) move.previousDate else move.date)
                } else if (move.occurrenceId != null) repository.moveOccurrence(
                    move.taskId, move.occurrenceId,
                    if (undoing) move.date else move.previousDate,
                    if (undoing) requireNotNull(move.previousDate) else move.date,
                    move.scope,
                ) else repository.reschedule(
                    move.taskId,
                    if (undoing) move.date else move.previousDate,
                    if (undoing) move.previousDate else move.date,
                )
                if (updated) null else if (!move.isEvent && move.scope == TaskChangeScope.ThisAndFollowing)
                    "This event changed, was deleted, or overlaps completed history. Reopen it and try again."
                else "This ${if (move.isEvent) "event" else "task"} has changed or was deleted. Please try again."
            } catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { if (undoing) "Couldn't undo the move. Please try again." else "Couldn't move this ${if (move.isEvent) "event" else "task"}. Please try again." }
            emit(CalendarMessage.Moved(move, undoing, error))
            if (error == null && !undoing) {
                delay(3_000)
                emit(CalendarMessage.ExpireUndo(move.token))
            }
        })
    }
}
