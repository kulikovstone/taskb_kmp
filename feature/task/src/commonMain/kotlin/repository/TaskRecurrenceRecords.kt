package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.feature.task.api.database.TaskOccurrenceEntity
import com.mkulikov.taskb.feature.task.api.database.TaskRecord
import com.mkulikov.taskb.feature.task.api.database.TaskScheduleEntity
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.model.overlapsDates
import com.mkulikov.taskb.feature.task.api.recurrence.recurrenceDates
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

internal fun TaskScheduleEntity.rule() = TaskRecurrence(
    frequency = RecurrenceFrequency.valueOf(frequency), interval = interval,
    weekdays = DayOfWeek.entries.filter { weekdays and (1 shl it.ordinal) != 0 }.toSet(),
    dayOfMonth = dayOfMonth, month = month, endDate = endDate?.let(LocalDate::fromEpochDays), occurrenceCount = occurrenceCount,
)

internal fun LocalTime.minuteOfDay() = hour * 60 + minute
internal fun Int.localTime() = LocalTime(this / 60, this % 60)
internal fun normalizedTaskDuration(startTimeMinutes: Int?, durationMinutes: Int?): Int? =
    if (startTimeMinutes == null) null else durationMinutes ?: 60

internal fun TaskRecurrence.entity(taskId: Int, start: LocalDate, startTime: LocalTime? = null, durationMinutes: Int? = null) = TaskScheduleEntity(
    taskId = taskId, startDate = start.toEpochDays(), frequency = frequency.name, interval = interval,
    weekdays = weekdays.fold(0) { mask, day -> mask or (1 shl day.ordinal) }, dayOfMonth = dayOfMonth, month = month,
    endDate = endDate?.toEpochDays(), occurrenceCount = occurrenceCount,
    startTimeMinutes = startTime?.minuteOfDay(), durationMinutes = normalizedTaskDuration(startTime?.minuteOfDay(), durationMinutes),
)

internal val TaskRecord.currentSchedule get() = schedules.filter { it.closedBefore == null }.maxByOrNull { it.id }
private val TaskRecord.displaySchedule get() = currentSchedule ?: schedules.maxByOrNull { it.id }?.takeIf { task.recurring }

internal fun TaskRecord.task(): Task {
    val time = if (task.recurring) displaySchedule?.startTimeMinutes else task.startTimeMinutes
    val duration = if (task.recurring) displaySchedule?.durationMinutes else task.durationMinutes
    return Task(
        id = task.id, name = task.name, dueDate = task.dueDate?.let(LocalDate::fromEpochDays), groupId = task.groupId,
        createdAt = Instant.fromEpochMilliseconds(task.createdAt), completedAt = task.completedAt?.let(Instant::fromEpochMilliseconds),
        recurrence = displaySchedule?.rule(),
        description = task.description,
        startTime = time?.localTime(), durationMinutes = normalizedTaskDuration(time, duration),
    )
}

private fun TaskRecord.exceptionIndex() = occurrences.associateBy { it.scheduleId to it.scheduledDate }

internal fun TaskRecord.occurrence(
    schedule: TaskScheduleEntity, date: LocalDate,
    exceptions: Map<Pair<Long, Long>, TaskOccurrenceEntity> = exceptionIndex(),
): Task? {
    if (schedule.closedBefore?.let { date.toEpochDays() >= it } == true) return null
    if (recurrenceDates(schedule.rule(), LocalDate.fromEpochDays(schedule.startDate), date, date).isEmpty()) return null
    val exception = exceptions[schedule.id to date.toEpochDays()]
    if (exception?.skipped == true) return null
    val time = if (exception?.overridesTiming == true) exception.startTimeMinutes else schedule.startTimeMinutes
    val duration = if (exception?.overridesTiming == true) exception.durationMinutes else schedule.durationMinutes
    return task().copy(
        dueDate = exception?.movedDate?.let(LocalDate::fromEpochDays) ?: date,
        completedAt = exception?.completedAt?.let(Instant::fromEpochMilliseconds),
        recurrence = schedule.rule(), occurrenceId = TaskOccurrenceId(schedule.id, date),
        startTime = time?.localTime(), durationMinutes = normalizedTaskDuration(time, duration),
    )
}

internal fun TaskRecord.calendar(from: LocalDate, through: LocalDate): List<Task> = buildList {
    val exceptions = exceptionIndex()
    val longestDuration = (schedules.mapNotNull { normalizedTaskDuration(it.startTimeMinutes, it.durationMinutes) } +
        occurrences.mapNotNull { normalizedTaskDuration(it.startTimeMinutes, it.durationMinutes) } +
        listOfNotNull(normalizedTaskDuration(task.startTimeMinutes, task.durationMinutes))).maxOrNull() ?: 0
    // Include starts before the window when their duration extends into the visible dates.
    val searchFrom = LocalDate.fromEpochDays(from.toEpochDays() - (longestDuration.toLong() + 1439) / 1440)
    if (!task.recurring) task().takeIf { it.dueDate == null || it.overlapsDates(from, through) }?.let(::add)
    for (schedule in schedules) {
        val end = minOf(through.toEpochDays(), schedule.closedBefore?.minus(1) ?: through.toEpochDays())
        if (end >= searchFrom.toEpochDays()) {
            for (date in recurrenceDates(schedule.rule(), LocalDate.fromEpochDays(schedule.startDate), searchFrom, LocalDate.fromEpochDays(end))) {
                occurrence(schedule, date, exceptions)?.takeIf { it.overlapsDates(from, through) }?.let(::add)
            }
        }
        for (moved in occurrences.filter { it.scheduleId == schedule.id && it.movedDate?.let { day -> day in searchFrom.toEpochDays()..through.toEpochDays() } == true }) {
            occurrence(schedule, LocalDate.fromEpochDays(moved.scheduledDate), exceptions)?.takeIf { it.overlapsDates(from, through) }?.let(::add)
        }
    }
}.distinctBy { it.key }

/** Latest pending overdue occurrence and the nearest pending occurrence from today onward. */
internal fun TaskRecord.taskList(today: LocalDate, completedSince: Long, includeTimed: Boolean = true): List<Task> {
    if (schedules.isEmpty()) return listOfNotNull(task().takeIf { includeTimed || !it.isTimed })
    val candidates = mutableListOf<Task>()
    val exceptions = exceptionIndex()
    if (!task.recurring) candidates += task()
    for (schedule in schedules) {
        val start = schedule.startDate
        val rule = schedule.rule()
        val naturalLimit = rule.occurrenceCount?.let { count ->
            val period = when (rule.frequency) {
                RecurrenceFrequency.Daily -> 1L
                RecurrenceFrequency.Weekly -> 7L
                RecurrenceFrequency.Monthly -> 31L
                RecurrenceFrequency.Yearly -> 366L
            }
            start + count.toLong() * rule.interval * period
        } ?: Long.MAX_VALUE
        val end = minOf(schedule.closedBefore?.minus(1) ?: Long.MAX_VALUE, schedule.endDate ?: Long.MAX_VALUE, naturalLimit, LocalDate(999_999, 12, 31).toEpochDays())
        fun search(future: Boolean): Task? {
            var boundary = if (future) maxOf(start, today.toEpochDays()) else minOf(end, today.toEpochDays() - 1)
            var size = 31L
            while (boundary in start..end) {
                val from = if (future) boundary else maxOf(start, boundary - size + 1)
                val until = if (future) minOf(end, boundary + size - 1) else boundary
                val values = recurrenceDates(rule, LocalDate.fromEpochDays(start), LocalDate.fromEpochDays(from), LocalDate.fromEpochDays(until))
                val ordered = if (future) values else values.asReversed()
                for (date in ordered) {
                    if (exceptions[schedule.id to date.toEpochDays()]?.movedDate != null) continue
                    occurrence(schedule, date, exceptions)?.takeIf {
                        !it.isCompleted && (includeTimed || !it.isTimed) && if (future) requireNotNull(it.dueDate) >= today else requireNotNull(it.dueDate) < today
                    }?.let { return it }
                }
                boundary = if (future) until + 1 else from - 1
                size = (size * 2).coerceAtMost(4_000_000)
            }
            return null
        }
        if (includeTimed || schedule.startTimeMinutes == null) {
            search(false)?.let(candidates::add)
            search(true)?.let(candidates::add)
        }
        for (exception in occurrences.filter { it.scheduleId == schedule.id && !it.skipped && (it.movedDate != null || it.overridesTiming || (it.completedAt ?: Long.MIN_VALUE) > completedSince) }) {
            occurrence(schedule, LocalDate.fromEpochDays(exception.scheduledDate), exceptions)?.let(candidates::add)
        }
    }
    val pending = candidates.filter { !it.isCompleted && (includeTimed || !it.isTimed) }
    return buildList {
        pending.filter { it.dueDate != null && requireNotNull(it.dueDate) < today }.maxByOrNull { requireNotNull(it.dueDate) }?.let(::add)
        pending.filter { it.dueDate == null || requireNotNull(it.dueDate) >= today }.minWithOrNull(compareBy<Task> { it.dueDate == null }.thenBy { it.dueDate })?.let(::add)
        addAll(candidates.filter { it.isCompleted && (includeTimed || !it.isTimed) && requireNotNull(it.completedAt).toEpochMilliseconds() > completedSince })
    }.distinctBy { it.key }
}
