package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

internal sealed interface CalendarMessage {
    data class TaskListLoaded(val tasks: List<Task>) : CalendarMessage

    data object TaskListLoading : CalendarMessage

    data object TaskListLoadFailed : CalendarMessage

    data class Holidays(
        val month: YearMonth,
        val selectedMonth: YearMonth?,
        val value: HolidayCalendar,
    ) : CalendarMessage

    data class Weather(val value: WeatherForecast) : CalendarMessage

    data class MoveChoice(val value: CalendarMove?) : CalendarMessage

    data object Loading : CalendarMessage

    data object LoadFailed : CalendarMessage

    data object DismissError : CalendarMessage

    data class Loaded(
        val tasks: List<Task>,
        val groups: List<Group>,
        val now: Instant,
        val events: List<Event> = emptyList(),
    ) : CalendarMessage

    data class Month(val value: YearMonth) : CalendarMessage

    data class SelectedDate(val value: LocalDate?) : CalendarMessage

    data class Today(val value: LocalDate, val now: Instant) : CalendarMessage

    data class Moving(val id: Int, val undoing: Boolean = false) : CalendarMessage

    data class Moved(val move: CalendarMove, val undoing: Boolean, val error: String? = null) : CalendarMessage

    data class ExpireUndo(val token: Long) : CalendarMessage

    data class UpdatingTask(val id: Int) : CalendarMessage

    data class Completed(val id: Int, val failed: Boolean) : CalendarMessage

    data class QuickDeleteConfirmation(val task: Task?) : CalendarMessage

    data class QuickDeleteFinished(
        val id: Int,
        val occurrenceId: TaskOccurrenceId,
        val failed: Boolean,
    ) : CalendarMessage
}

internal fun reduceCalendar(state: CalendarState, message: CalendarMessage): CalendarState {
    return when (message) {
        is CalendarMessage.TaskListLoaded -> state.copy(taskList = message.tasks, taskListLoadError = null)
        CalendarMessage.TaskListLoading -> state.copy(taskList = null, taskListLoadError = null)
        CalendarMessage.TaskListLoadFailed -> state.copy(
            taskListLoadError = "Couldn't load tasks. Please try again.",
        )
        is CalendarMessage.Holidays -> if (
            message.month != state.month ||
                message.selectedMonth != state.selectedDate?.yearMonth?.takeUnless { month -> month == state.month }
        ) {
            state
        } else {
            state.copy(holidayCalendar = message.value)
        }
        is CalendarMessage.Weather -> state.copy(weather = message.value)
        is CalendarMessage.MoveChoice -> state.copy(moveChoice = message.value)
        CalendarMessage.Loading -> state.copy(isLoading = true, loadError = null)
        CalendarMessage.LoadFailed -> state.copy(
            isLoading = false,
            loadError = "Couldn't load the calendar. Please try again.",
        )
        CalendarMessage.DismissError -> state.copy(error = null)
        is CalendarMessage.Loaded -> state.copy(
            tasks = message.tasks,
            events = message.events,
            groups = message.groups,
            now = message.now,
            isLoading = false,
            loadError = null,
        )
        is CalendarMessage.Month -> state.copy(month = message.value)
        is CalendarMessage.SelectedDate -> state.copy(selectedDate = message.value)
        is CalendarMessage.Today -> state.copy(today = message.value, now = message.now)
        is CalendarMessage.Moving -> state.copy(
            pendingMove = message.id,
            undo = if (message.undoing) null else state.undo,
        )
        is CalendarMessage.Moved -> state.copy(
            pendingMove = null,
            error = message.error,
            undo = if (message.undoing || message.move.scope == TaskChangeScope.ThisAndFollowing) {
                null
            } else if (message.error == null) {
                message.move
            } else {
                state.undo
            },
            tasks = if (
                message.move.isEvent ||
                    message.error != null ||
                    message.move.scope == TaskChangeScope.ThisAndFollowing
            ) {
                state.tasks
            } else {
                state.tasks.map { task ->
                    if (task.id == message.move.taskId && task.occurrenceId == message.move.occurrenceId) {
                        task.copy(
                            dueDate = if (message.undoing) message.move.previousDate else message.move.date
                        )
                    } else {
                        task
                    }
                }
            },
            events = if (
                !message.move.isEvent ||
                    message.error != null ||
                    message.move.scope == TaskChangeScope.ThisAndFollowing
            ) {
                state.events
            } else {
                state.events.map { event ->
                    if (
                        event.id == message.move.taskId &&
                            event.occurrenceId == message.move.eventOccurrenceId
                    ) {
                        event.copy(
                            dueDate = if (message.undoing) message.move.previousDate else message.move.date
                        )
                    } else {
                        event
                    }
                }
            },
        )
        is CalendarMessage.ExpireUndo -> if (state.undo?.token == message.token) state.copy(undo = null) else state
        is CalendarMessage.UpdatingTask -> state.copy(pendingTaskUpdates = state.pendingTaskUpdates + message.id)
        is CalendarMessage.Completed -> state.copy(
            pendingTaskUpdates = state.pendingTaskUpdates - message.id,
            error = if (message.failed) "Couldn't update this task. Please try again." else state.error,
        )
        is CalendarMessage.QuickDeleteConfirmation -> state.copy(quickDeleteConfirmation = message.task)
        is CalendarMessage.QuickDeleteFinished -> {
            val pending = state.pendingTaskUpdates - message.id
            if (message.failed) {
                state.copy(pendingTaskUpdates = pending, error = "Couldn't delete this occurrence. Please try again.")
            } else {
                val keepTask = { task: Task -> task.id != message.id || task.occurrenceId != message.occurrenceId }
                state.copy(
                    pendingTaskUpdates = pending,
                    tasks = state.tasks.filter(predicate = keepTask),
                    taskList = state.taskList?.filter(predicate = keepTask),
                )
            }
        }
    }
}
