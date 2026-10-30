package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

internal data class CalendarState(
    val today: LocalDate,
    val month: YearMonth = today.yearMonth,
    val tasks: List<Task> = emptyList(),
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val error: String? = null,
    val pendingMove: Int? = null,
    val pendingCompletion: Set<Int> = emptySet(),
    val undo: CalendarMove? = null,
) {
    val undatedTasks: List<Task> get() = tasks.filter { it.dueDate == null }
        .sortedWith(compareByDescending<Task> { it.createdAt }.thenByDescending { it.id })
    val tasksByDate: Map<LocalDate?, List<Task>> get() = tasks.filter { it.dueDate != null }
        .sortedWith(compareBy<Task> { it.isCompleted }.thenByDescending { it.createdAt }.thenByDescending { it.id })
        .groupBy { it.dueDate }
    val days: List<LocalDate> get() {
        val offset = month.firstDay.dayOfWeek.isoDayNumber - 1
        val first = month.firstDay.minus(offset, DateTimeUnit.DAY)
        val count = ((offset + month.numberOfDays + 6) / 7) * 7
        return List(count) { first.plus(it, DateTimeUnit.DAY) }
    }
}

internal data class CalendarMove(
    val token: Long,
    val taskId: Int,
    val previousDate: LocalDate?,
    val date: LocalDate,
)
