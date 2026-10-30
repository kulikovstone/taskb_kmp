package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

internal sealed interface CalendarMessage {
    data object Loading : CalendarMessage
    data object LoadFailed : CalendarMessage
    data object DismissError : CalendarMessage
    data class Loaded(val tasks: List<Task>, val groups: List<Group>) : CalendarMessage
    data class Month(val value: YearMonth) : CalendarMessage
    data class Today(val value: LocalDate) : CalendarMessage
    data class Moving(val id: Int, val undoing: Boolean = false) : CalendarMessage
    data class Moved(val move: CalendarMove, val undoing: Boolean, val error: String? = null) : CalendarMessage
    data class ExpireUndo(val token: Long) : CalendarMessage
    data class Completing(val id: Int) : CalendarMessage
    data class Completed(val id: Int, val failed: Boolean) : CalendarMessage
}

internal fun reduceCalendar(state: CalendarState, message: CalendarMessage): CalendarState = when (message) {
    CalendarMessage.Loading -> state.copy(isLoading = true, loadError = null)
    CalendarMessage.LoadFailed -> state.copy(isLoading = false, loadError = "Couldn't load the calendar. Please try again.")
    CalendarMessage.DismissError -> state.copy(error = null)
    is CalendarMessage.Loaded -> state.copy(tasks = message.tasks, groups = message.groups, isLoading = false, loadError = null)
    is CalendarMessage.Month -> state.copy(month = message.value)
    is CalendarMessage.Today -> state.copy(today = message.value)
    is CalendarMessage.Moving -> state.copy(pendingMove = message.id, undo = if (message.undoing) null else state.undo)
    is CalendarMessage.Moved -> state.copy(
        pendingMove = null,
        error = message.error,
        undo = if (message.undoing) null else if (message.error == null) message.move else state.undo,
        tasks = if (message.error != null) state.tasks else state.tasks.map { task ->
            if (task.id == message.move.taskId) task.copy(dueDate = if (message.undoing) message.move.previousDate else message.move.date) else task
        },
    )
    is CalendarMessage.ExpireUndo -> if (state.undo?.token == message.token) state.copy(undo = null) else state
    is CalendarMessage.Completing -> state.copy(pendingCompletion = state.pendingCompletion + message.id)
    is CalendarMessage.Completed -> state.copy(
        pendingCompletion = state.pendingCompletion - message.id,
        error = if (message.failed) "Couldn't update this task. Please try again." else state.error,
    )
}
