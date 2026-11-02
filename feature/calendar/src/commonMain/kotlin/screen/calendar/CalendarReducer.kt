package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Instant

internal sealed interface CalendarMessage {
    data class MoveChoice(val value: CalendarMove?) : CalendarMessage
    data object Loading : CalendarMessage
    data object LoadFailed : CalendarMessage
    data object DismissError : CalendarMessage
    data class Loaded(val tasks: List<Task>, val groups: List<Group>, val now: Instant) : CalendarMessage
    data class Month(val value: YearMonth) : CalendarMessage
    data class SelectedDate(val value: LocalDate?) : CalendarMessage
    data class Today(val value: LocalDate, val now: Instant) : CalendarMessage
    data class Moving(val id: Int, val undoing: Boolean = false) : CalendarMessage
    data class Moved(val move: CalendarMove, val undoing: Boolean, val error: String? = null) : CalendarMessage
    data class ExpireUndo(val token: Long) : CalendarMessage
    data class Completing(val id: Int) : CalendarMessage
    data class Completed(val id: Int, val failed: Boolean) : CalendarMessage
}

internal fun reduceCalendar(state: CalendarState, message: CalendarMessage): CalendarState = when (message) {
    is CalendarMessage.MoveChoice -> state.copy(moveChoice = message.value)
    CalendarMessage.Loading -> state.copy(isLoading = true, loadError = null)
    CalendarMessage.LoadFailed -> state.copy(isLoading = false, loadError = "Couldn't load the calendar. Please try again.")
    CalendarMessage.DismissError -> state.copy(error = null)
    is CalendarMessage.Loaded -> state.copy(tasks = message.tasks, groups = message.groups, now = message.now, isLoading = false, loadError = null)
    is CalendarMessage.Month -> state.copy(month = message.value)
    is CalendarMessage.SelectedDate -> state.copy(selectedDate = message.value)
    is CalendarMessage.Today -> state.copy(today = message.value, now = message.now)
    is CalendarMessage.Moving -> state.copy(pendingMove = message.id, undo = if (message.undoing) null else state.undo)
    is CalendarMessage.Moved -> state.copy(
        pendingMove = null,
        error = message.error,
        undo = if (message.undoing || message.move.scope == TaskChangeScope.ThisAndFollowing) null else if (message.error == null) message.move else state.undo,
        tasks = if (message.error != null || message.move.scope == TaskChangeScope.ThisAndFollowing) state.tasks else state.tasks.map { task ->
            if (task.id == message.move.taskId && task.occurrenceId == message.move.occurrenceId) task.copy(dueDate = if (message.undoing) message.move.previousDate else message.move.date) else task
        },
    )
    is CalendarMessage.ExpireUndo -> if (state.undo?.token == message.token) state.copy(undo = null) else state
    is CalendarMessage.Completing -> state.copy(pendingCompletion = state.pendingCompletion + message.id)
    is CalendarMessage.Completed -> state.copy(
        pendingCompletion = state.pendingCompletion - message.id,
        error = if (message.failed) "Couldn't update this task. Please try again." else state.error,
    )
}
