package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.feature.item.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.api.model.occursOn
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

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
    val pendingTaskUpdates: Set<Int> = emptySet(),
    val quickDeleteConfirmation: Task? = null,
    val undo: CalendarMove? = null,
    val moveChoice: CalendarMove? = null,
    val now: Instant = Clock.System.now(),
    val weather: WeatherForecast = WeatherForecast(),
    val holidayCalendar: HolidayCalendar = HolidayCalendar(),
    val taskList: List<Task>? = null,
    val taskListLoadError: String? = null,
) {
    val isContentLoading: Boolean
        get() = isLoading || (taskList == null && taskListLoadError == null)

    fun canUndo(move: CalendarMove): Boolean {
        return pendingMove == null && (move.type != ItemType.Task || move.itemId !in pendingTaskUpdates)
    }

    val weatherByDate
        get() = weather.location
            ?.let { location ->
                val first = now.toLocalDateTime(timeZone = TimeZone.of(zoneId = location.timeZone)).date
                val end = first.plus(value = WeatherForecast.HORIZON_DAYS, unit = DateTimeUnit.DAY)
                weather.days.filter { weather -> weather.date in first..<end }.associateBy { weather -> weather.date }
            }
            .orEmpty()

    val undatedTasks: List<Task>
        get() = tasks
            .filter { task ->
                val completedAt = task.completedAt
                task.dueDate == null && (completedAt == null || completedAt > now - Task.listVisibilityAfterCompletion)
            }
            .sortedWith(
                comparator = compareBy<Task>(selector = Task::isCompleted)
                    .thenByDescending(selector = Task::createdAt)
                    .thenByDescending(selector = Task::id)
            )

    val nextCompletionExpiry: Instant?
        get() = tasks
            .asSequence()
            .filter { task -> task.dueDate == null }
            .plus(elements = taskList.orEmpty())
            .mapNotNull { task -> task.completedAt?.plus(duration = Task.listVisibilityAfterCompletion) }
            .filter { instant -> instant > now }
            .minOrNull()

    val tasksByDate: Map<LocalDate?, List<Task>>
        get() = tasks
            .filter { task -> task.dueDate != null }
            .groupBy(keySelector = Task::dueDate)
            .mapValues { (_, tasks) -> tasks.sortedWith(comparator = calendarTaskOrder) }

    val monthTasksByDate: Map<LocalDate?, List<Task>>
        get() = tasksByDate
            .mapValues { (date, tasks) ->
                val isPastDay = date != null && date < today
                tasks.filter { task ->
                    task.showInCalendar || (isPastDay && !task.isCompleted)
                }
            }
            .filterValues { tasks -> tasks.isNotEmpty() }

    val sidebarTasks: List<Task>
        get() = selectedDate?.let { date -> tasksByDate[date].orEmpty().sortedWith(comparator = sidebarTaskOrder) }
            ?: undatedTasks

    val sidebarRecurringTasks: List<Task>
        get() = if (selectedDate == null) {
            emptyList()
        } else {
            sidebarTasks.filter { task -> !task.isCompleted && task.isRecurring }
        }

    val sidebarUntimedTasks: List<Task>
        get() = sidebarTasks.filter { task -> !task.isCompleted && (selectedDate == null || !task.isRecurring) }

    val sidebarCompletedTasks: List<Task>
        get() = sidebarTasks
            .filter(predicate = Task::isCompleted)
            .sortedWith(
                comparator = compareByDescending<Task>(selector = Task::completedAt)
                    .thenByDescending(selector = Task::createdAt)
                    .thenByDescending(selector = Task::id)
            )

    private fun eventItems(date: LocalDate?): List<CalendarItem.EventItem> {
        return events
            .filter { event -> if (date == null) event.dueDate == null else event.occursOn(date = date) }
            .map(transform = CalendarItem::EventItem)
    }

    private fun holidayItems(date: LocalDate?): List<CalendarItem.HolidayItem> {
        return holidayCalendar.holidays
            .filter { holiday -> holiday.date == date }
            .distinctBy(selector = PublicHoliday::id)
            .sortedBy(selector = PublicHoliday::name)
            .map(transform = CalendarItem::HolidayItem)
    }

    val monthItemsByDate: Map<LocalDate?, List<CalendarItem>>
        get() {
            val tasksByDay = monthTasksByDate
            return days.associateWith { date ->
                val order = if (date != null && date < today) pastDayCalendarItemOrder else calendarItemOrder
                (tasksByDay[date].orEmpty().map(transform = CalendarItem::TaskItem) +
                        holidayItems(date = date) +
                        eventItems(date = date))
                    .sortedWith(comparator = order)
            }
        }

    val sidebarItems: List<CalendarItem>
        get() = (sidebarTasks.map(transform = CalendarItem::TaskItem) +
                holidayItems(date = selectedDate) +
                eventItems(date = selectedDate))
            .sortedWith(comparator = calendarItemOrder)

    val sidebarRecurringItems
        get() = sidebarRecurringTasks.map(transform = CalendarItem::TaskItem)

    val sidebarEventItems
        get() = (holidayItems(date = selectedDate) + eventItems(date = selectedDate)).sortedWith(
            comparator = calendarItemOrder
        )

    val sidebarMainTaskItems
        get() = sidebarUntimedTasks.map(transform = CalendarItem::TaskItem)

    val sidebarCompletedItems
        get() = sidebarCompletedTasks.map(transform = CalendarItem::TaskItem)

    val isSelectedDayCompleted: Boolean
        get() {
            if (selectedDate != today || isLoading || loadError != null) return false
            val tasks = sidebarTasks
            val timedEvents =
                sidebarEventItems.filterIsInstance<CalendarItem.EventItem>().map { item ->
                    item.event
                }.filter(predicate = Event::isTimed)
            return (tasks.isNotEmpty() || timedEvents.isNotEmpty()) &&
                tasks.all(predicate = Task::isCompleted) &&
                timedEvents.all { event -> event.isPastCalendarEvent(now = now) }
        }

    val days: List<LocalDate>
        get() {
            val offset = month.firstDay.dayOfWeek.isoDayNumber - 1
            val first = month.firstDay.minus(value = offset, unit = DateTimeUnit.DAY)
            val count = ((offset + month.numberOfDays + 6) / 7) * 7
            return List(size = count) { index -> first.plus(value = index, unit = DateTimeUnit.DAY) }
        }
}

private val calendarTaskOrder =
    compareBy<Task> { task ->
        task.isCompleted
    }.thenByDescending(selector = Task::createdAt).thenByDescending(selector = Task::id)

private val sidebarTaskOrder = compareBy<Task> { task -> !task.isRecurring }.then(comparator = calendarTaskOrder)

internal val Task.isRecurring: Boolean
    get() = recurrence != null

private val Task.repeatsDaily: Boolean
    get() = recurrence?.frequency == RecurrenceFrequency.Daily

internal data class CalendarMove(
    val token: Long,
    val itemId: Int,
    val previousDate: LocalDate?,
    val date: LocalDate,
    val occurrenceId: ItemOccurrenceId? = null,
    val scope: ItemChangeScope = ItemChangeScope.ThisOccurrence,
    val type: ItemType,
)

private val calendarItemOrder = compareBy<CalendarItem> { item ->
        when {
            item.isHoliday -> -1
            !item.isEvent -> 2
            item.startDateTime == null -> 0
            else -> 1
        }
    }
    .thenBy(selector = CalendarItem::startDateTime)
    .thenBy(selector = CalendarItem::isCompleted)
    .thenByDescending(selector = CalendarItem::createdAt)
    .thenByDescending { item -> if (item.isHoliday) 0 else item.id }
    .thenBy { item -> if (item.isHoliday) item.name else "" }

private val pastDayCalendarItemOrder = compareBy<CalendarItem> { item ->
    when {
        item !is CalendarItem.TaskItem || item.isCompleted -> 2
        item.task.repeatsDaily -> 1
        else -> 0
    }
}.then(
    comparator = calendarItemOrder,
)
