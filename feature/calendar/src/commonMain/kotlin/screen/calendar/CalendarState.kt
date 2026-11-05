package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.event.api.model.occursOn
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal data class CalendarState(
    val today: LocalDate,
    val month: YearMonth = today.yearMonth,
    val selectedDate: LocalDate? = today,
    val tasks: List<Task> = emptyList(),
    val groups: List<Group> = emptyList(),
    val events: List<Event> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val error: String? = null,
    val pendingMove: Int? = null,
    val pendingCompletion: Set<Int> = emptySet(),
    val undo: CalendarMove? = null,
    val moveChoice: CalendarMove? = null,
    val now: Instant = Clock.System.now(),
) {
    val undatedTasks: List<Task> get() = tasks.filter {
        val completedAt = it.completedAt
        it.dueDate == null && (completedAt == null || completedAt > now - 24.hours)
    }.sortedWith(compareBy<Task> { it.isCompleted }.thenByDescending { it.createdAt }.thenByDescending { it.id })
    val nextCompletionExpiry: Instant? get() = tasks.asSequence()
        .filter { it.dueDate == null }
        .mapNotNull { it.completedAt?.plus(24.hours) }
        .filter { it > now }
        .minOrNull()
    val tasksByDate: Map<LocalDate?, List<Task>> get() = tasks.filter { it.dueDate != null }
        .groupBy { it.dueDate }.mapValues { (_, tasks) -> tasks.sortedWith(calendarTaskOrder) }
    val monthTasksByDate: Map<LocalDate?, List<Task>> get() = tasksByDate
        .mapValues { (_, tasks) -> tasks.filterNot { it.isDailyRoutine } }
        .filterValues { it.isNotEmpty() }
    val sidebarTasks: List<Task> get() = selectedDate?.let { tasksByDate[it].orEmpty().sortedWith(sidebarTaskOrder) } ?: undatedTasks
    val sidebarDailyTasks: List<Task> get() = if (selectedDate == null) emptyList() else sidebarTasks.filter { !it.isCompleted && it.isDailyRoutine }
    val sidebarUntimedTasks: List<Task> get() = sidebarTasks.filter { !it.isCompleted && (selectedDate == null || !it.isDailyRoutine) }
    val sidebarCompletedTasks: List<Task> get() = sidebarTasks.filter { it.isCompleted }
        .sortedWith(compareByDescending<Task> { it.completedAt }.thenByDescending { it.createdAt }.thenByDescending { it.id })
    private fun eventItems(date: LocalDate?): List<CalendarItem.EventItem> = events
        .filter { if (date == null) it.dueDate == null else it.occursOn(date) }
        .map(CalendarItem::EventItem)
    val monthItemsByDate: Map<LocalDate?, List<CalendarItem>> get() {
        val tasksByDay = monthTasksByDate
        return days.associateWith { date ->
            (tasksByDay[date].orEmpty().map(CalendarItem::TaskItem) + eventItems(date)).sortedWith(calendarItemOrder)
        }
    }
    val sidebarItems: List<CalendarItem> get() =
        (sidebarTasks.map(CalendarItem::TaskItem) + eventItems(selectedDate)).sortedWith(calendarItemOrder)
    val sidebarDailyItems get() = sidebarDailyTasks.map(CalendarItem::TaskItem)
    val sidebarEventItems get() = eventItems(selectedDate).sortedWith(calendarItemOrder)
    val sidebarMainTaskItems get() = sidebarUntimedTasks.map(CalendarItem::TaskItem)
    val sidebarCompletedItems get() = sidebarCompletedTasks.map(CalendarItem::TaskItem)
    val isSelectedDayCompleted: Boolean get() {
        if (selectedDate != today || isLoading || loadError != null) return false
        val tasks = sidebarTasks
        val timedEvents = sidebarEventItems.map { it.event }.filter { it.isTimed }
        return (tasks.isNotEmpty() || timedEvents.isNotEmpty()) &&
            tasks.all { it.isCompleted } && timedEvents.all { it.isPastCalendarEvent(now) }
    }
    val days: List<LocalDate> get() {
        val offset = month.firstDay.dayOfWeek.isoDayNumber - 1
        val first = month.firstDay.minus(offset, DateTimeUnit.DAY)
        val count = ((offset + month.numberOfDays + 6) / 7) * 7
        return List(count) { first.plus(it, DateTimeUnit.DAY) }
    }
}

private val calendarTaskOrder = compareBy<Task> { it.isCompleted }
    .thenByDescending { it.createdAt }
    .thenByDescending { it.id }

private val sidebarTaskOrder = compareBy<Task> { !it.isDailyRoutine }.then(calendarTaskOrder)

internal val Task.isDailyRoutine: Boolean
    get() = recurrence?.let { it.frequency == RecurrenceFrequency.Daily && it.interval == 1 } == true

internal data class CalendarMove(
    val token: Long,
    val taskId: Int,
    val previousDate: LocalDate?,
    val date: LocalDate,
    val occurrenceId: TaskOccurrenceId? = null,
    val scope: TaskChangeScope = TaskChangeScope.ThisOccurrence,
    val isEvent: Boolean = false,
    val eventOccurrenceId: EventOccurrenceId? = null,
)

private val calendarItemOrder = compareBy<CalendarItem> {
    when {
        !it.isEvent -> 2
        it.startDateTime == null -> 0
        else -> 1
    }
}
    .thenBy { it.startDateTime }.thenBy { it.isCompleted }
    .thenByDescending { it.createdAt }.thenByDescending { it.id }
