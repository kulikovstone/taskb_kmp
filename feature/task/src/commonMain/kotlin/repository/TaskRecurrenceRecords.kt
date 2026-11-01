package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.feature.task.api.database.TaskOccurrenceEntity
import com.mkulikov.taskb.feature.task.api.database.TaskRecord
import com.mkulikov.taskb.feature.task.api.database.TaskScheduleEntity
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.recurrence.recurrenceDates
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal fun TaskScheduleEntity.rule() = TaskRecurrence(
    frequency = RecurrenceFrequency.valueOf(frequency), interval = interval,
    weekdays = DayOfWeek.entries.filter { weekdays and (1 shl it.ordinal) != 0 }.toSet(),
    dayOfMonth = dayOfMonth, month = month, endDate = endDate?.let(LocalDate::fromEpochDays), occurrenceCount = occurrenceCount,
)

internal fun TaskRecurrence.entity(taskId: Int, start: LocalDate) = TaskScheduleEntity(
    taskId = taskId, startDate = start.toEpochDays(), frequency = frequency.name, interval = interval,
    weekdays = weekdays.fold(0) { mask, day -> mask or (1 shl day.ordinal) }, dayOfMonth = dayOfMonth, month = month,
    endDate = endDate?.toEpochDays(), occurrenceCount = occurrenceCount,
)

internal val TaskRecord.currentSchedule get() = schedules.filter { it.closedBefore == null }.maxByOrNull { it.id }

internal fun TaskRecord.task() = Task(
    id = task.id, name = task.name, dueDate = task.dueDate?.let(LocalDate::fromEpochDays), groupId = task.groupId,
    createdAt = Instant.fromEpochMilliseconds(task.createdAt), completedAt = task.completedAt?.let(Instant::fromEpochMilliseconds),
    recurrence = (currentSchedule ?: schedules.maxByOrNull { it.id }?.takeIf { task.recurring })?.rule(),
)

private fun TaskRecord.exceptionIndex() = occurrences.associateBy { it.scheduleId to it.scheduledDate }

internal fun TaskRecord.occurrence(
    schedule: TaskScheduleEntity, date: LocalDate,
    exceptions: Map<Pair<Long, Long>, TaskOccurrenceEntity> = exceptionIndex(),
): Task? {
    if (schedule.closedBefore?.let { date.toEpochDays() >= it } == true) return null
    if (recurrenceDates(schedule.rule(), LocalDate.fromEpochDays(schedule.startDate), date, date).isEmpty()) return null
    val exception = exceptions[schedule.id to date.toEpochDays()]
    if (exception?.skipped == true) return null
    return task().copy(
        dueDate = exception?.movedDate?.let(LocalDate::fromEpochDays) ?: date,
        completedAt = exception?.completedAt?.let(Instant::fromEpochMilliseconds),
        recurrence = schedule.rule(), occurrenceId = TaskOccurrenceId(schedule.id, date),
    )
}

internal fun TaskRecord.calendar(from: LocalDate, through: LocalDate): List<Task> = buildList {
    val exceptions = exceptionIndex()
    if (!task.recurring) task().takeIf { it.dueDate == null || it.dueDate?.let { day -> day in from..through } == true }?.let(::add)
    for (schedule in schedules) {
        val end = minOf(through.toEpochDays(), schedule.closedBefore?.minus(1) ?: through.toEpochDays())
        if (end >= from.toEpochDays()) {
            for (date in recurrenceDates(schedule.rule(), LocalDate.fromEpochDays(schedule.startDate), from, LocalDate.fromEpochDays(end))) {
                occurrence(schedule, date, exceptions)?.takeIf { it.dueDate?.let { day -> day in from..through } == true }?.let(::add)
            }
        }
        for (moved in occurrences.filter { it.scheduleId == schedule.id && it.movedDate?.let { day -> day in from.toEpochDays()..through.toEpochDays() } == true }) {
            occurrence(schedule, LocalDate.fromEpochDays(moved.scheduledDate), exceptions)?.let(::add)
        }
    }
}.distinctBy { it.key }

/** Latest pending overdue occurrence and the nearest pending occurrence from today onward. */
internal fun TaskRecord.taskList(today: LocalDate, completedSince: Long): List<Task> {
    if (schedules.isEmpty()) return listOf(task())
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
                        !it.isCompleted && if (future) requireNotNull(it.dueDate) >= today else requireNotNull(it.dueDate) < today
                    }?.let { return it }
                }
                boundary = if (future) until + 1 else from - 1
                size = (size * 2).coerceAtMost(4_000_000)
            }
            return null
        }
        search(false)?.let(candidates::add)
        search(true)?.let(candidates::add)
        for (exception in occurrences.filter { it.scheduleId == schedule.id && !it.skipped && (it.movedDate != null || (it.completedAt ?: Long.MIN_VALUE) > completedSince) }) {
            occurrence(schedule, LocalDate.fromEpochDays(exception.scheduledDate), exceptions)?.let(candidates::add)
        }
    }
    val pending = candidates.filter { !it.isCompleted }
    return buildList {
        pending.filter { it.dueDate != null && requireNotNull(it.dueDate) < today }.maxByOrNull { requireNotNull(it.dueDate) }?.let(::add)
        pending.filter { it.dueDate == null || requireNotNull(it.dueDate) >= today }.minWithOrNull(compareBy<Task> { it.dueDate == null }.thenBy { it.dueDate })?.let(::add)
        addAll(candidates.filter { it.isCompleted && requireNotNull(it.completedAt).toEpochMilliseconds() > completedSince })
    }.distinctBy { it.key }
}
