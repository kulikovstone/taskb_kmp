package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.feature.task.api.database.TaskOccurrenceEntity
import com.mkulikov.taskb.feature.task.api.database.TaskRecord
import com.mkulikov.taskb.feature.task.api.database.TaskScheduleEntity
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.recurrence.recurrenceDates
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

internal fun TaskScheduleEntity.rule(): TaskRecurrence {
    return TaskRecurrence(
        frequency = RecurrenceFrequency.valueOf(value = frequency),
        interval = interval,
        weekdays = DayOfWeek.entries.filter { weekday -> weekdays and (1 shl weekday.ordinal) != 0 }.toSet(),
        dayOfMonth = dayOfMonth,
        month = month,
        endDate = endDate?.let(block = LocalDate::fromEpochDays),
        occurrenceCount = occurrenceCount,
    )
}

internal fun TaskRecurrence.entity(taskId: Int, start: LocalDate): TaskScheduleEntity {
    return TaskScheduleEntity(
        taskId = taskId,
        startDate = start.toEpochDays(),
        frequency = frequency.name,
        interval = interval,
        weekdays = weekdays.fold(initial = 0) { mask, day -> mask or (1 shl day.ordinal) },
        dayOfMonth = dayOfMonth,
        month = month,
        endDate = endDate?.toEpochDays(),
        occurrenceCount = occurrenceCount,
    )
}

internal val TaskRecord.currentSchedule
    get() = schedules.filter { schedule -> schedule.closedBefore == null }.maxByOrNull { schedule -> schedule.id }
private val TaskRecord.displaySchedule
    get() = currentSchedule ?: schedules.maxByOrNull(selector = TaskScheduleEntity::id)?.takeIf { task.recurring }

internal fun TaskRecord.task(): Task {
    return Task(
        id = task.id,
        name = task.name,
        dueDate = task.dueDate?.let(block = LocalDate::fromEpochDays),
        groupId = task.groupId,
        createdAt = Instant.fromEpochMilliseconds(epochMilliseconds = task.createdAt),
        completedAt = task.completedAt?.let(block = Instant::fromEpochMilliseconds),
        recurrence = displaySchedule?.rule(),
        description = task.description,
        showInCalendar = task.showInCalendar,
        quickDelete = task.quickDeleteOverdue,
    )
}

private fun TaskRecord.exceptionIndex(): Map<Pair<Long, Long>, TaskOccurrenceEntity> {
    return occurrences.associateBy { occurrence -> occurrence.scheduleId to occurrence.scheduledDate }
}

internal fun TaskRecord.occurrence(
    schedule: TaskScheduleEntity,
    date: LocalDate,
    exceptions: Map<Pair<Long, Long>, TaskOccurrenceEntity> = exceptionIndex(),
): Task? {
    if (schedule.closedBefore?.let { closedBefore -> date.toEpochDays() >= closedBefore } == true) return null
    if (
        recurrenceDates(
                rule = schedule.rule(),
                start = LocalDate.fromEpochDays(epochDays = schedule.startDate),
                from = date,
                through = date,
            )
            .isEmpty()
    )
        return null
    val exception = exceptions[schedule.id to date.toEpochDays()]
    if (exception?.skipped == true) return null
    return task()
        .copy(
            dueDate = exception?.movedDate?.let(block = LocalDate::fromEpochDays) ?: date,
            completedAt = exception?.completedAt?.let(block = Instant::fromEpochMilliseconds),
            recurrence = schedule.rule(),
            occurrenceId = TaskOccurrenceId(scheduleId = schedule.id, scheduledDate = date),
        )
}

internal fun TaskRecord.calendar(from: LocalDate, through: LocalDate): List<Task> {
    return buildList {
        val exceptions = exceptionIndex()
        if (!task.recurring)
            task()
                .takeIf { task -> task.dueDate == null || task.dueDate?.let { date -> date in from..through } == true }
                ?.let(block = ::add)
        for (schedule in schedules) {
            val end = minOf(
                a = through.toEpochDays(),
                b = schedule.closedBefore?.minus(other = 1) ?: through.toEpochDays(),
            )
            if (end >= from.toEpochDays()) {
                for (date in
                    recurrenceDates(
                        rule = schedule.rule(),
                        start = LocalDate.fromEpochDays(epochDays = schedule.startDate),
                        from = from,
                        through = LocalDate.fromEpochDays(epochDays = end),
                    )) {
                    occurrence(schedule = schedule, date = date, exceptions = exceptions)
                        ?.takeIf { task -> task.dueDate?.let { date -> date in from..through } == true }
                        ?.let(block = ::add)
                }
            }
            for (moved in
                occurrences.filter { occurrence ->
                    occurrence.scheduleId == schedule.id &&
                        occurrence.movedDate?.let { day -> day in from.toEpochDays()..through.toEpochDays() } == true
                }) {
                occurrence(
                        schedule = schedule,
                        date = LocalDate.fromEpochDays(epochDays = moved.scheduledDate),
                        exceptions = exceptions,
                    )
                    ?.takeIf { task -> task.dueDate?.let { date -> date in from..through } == true }
                    ?.let(block = ::add)
            }
        }
    }
        .distinctBy(selector = Task::key)
}

/** Latest pending overdue occurrence and the nearest pending occurrence from today onward. */
internal fun TaskRecord.taskList(today: LocalDate, completedSince: Long): List<Task> {
    if (schedules.isEmpty()) return listOf(element = task())
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
        val end = minOf(
            a = schedule.closedBefore?.minus(other = 1) ?: Long.MAX_VALUE,
            schedule.endDate ?: Long.MAX_VALUE,
            naturalLimit,
            LocalDate(year = 999_999, month = 12, day = 31).toEpochDays(),
        )
        fun search(future: Boolean): Task? {
            var boundary = if (future) {
                maxOf(a = start, b = today.toEpochDays())
            } else {
                minOf(a = end, b = today.toEpochDays() - 1)
            }
            var size = 31L
            while (boundary in start..end) {
                val from = if (future) boundary else maxOf(a = start, b = boundary - size + 1)
                val until = if (future) minOf(a = end, b = boundary + size - 1) else boundary
                val values = recurrenceDates(
                    rule = rule,
                    start = LocalDate.fromEpochDays(epochDays = start),
                    from = LocalDate.fromEpochDays(epochDays = from),
                    through = LocalDate.fromEpochDays(epochDays = until),
                )
                val ordered = if (future) values else values.asReversed()
                for (date in ordered) {
                    if (exceptions[schedule.id to date.toEpochDays()]?.movedDate != null) continue
                    occurrence(schedule = schedule, date = date, exceptions = exceptions)
                        ?.takeIf { task ->
                            !task.isCompleted &&
                                if (future) {
                                    requireNotNull(value = task.dueDate) >= today
                                } else {
                                    requireNotNull(value = task.dueDate) < today
                                }
                        }
                        ?.let { task ->
                            return task
                        }
                }
                boundary = if (future) until + 1 else from - 1
                size = (size * 2).coerceAtMost(maximumValue = 4_000_000)
            }
            return null
        }
        search(future = false)?.let(block = candidates::add)
        search(future = true)?.let(block = candidates::add)
        for (exception in
            occurrences.filter { occurrence ->
                occurrence.scheduleId == schedule.id &&
                    !occurrence.skipped &&
                    (occurrence.movedDate != null || (occurrence.completedAt ?: Long.MIN_VALUE) > completedSince)
            }) {
            occurrence(
                    schedule = schedule,
                    date = LocalDate.fromEpochDays(epochDays = exception.scheduledDate),
                    exceptions = exceptions,
                )
                ?.let(block = candidates::add)
        }
    }
    val pending = candidates.filter { task -> !task.isCompleted }
    return buildList {
        pending
            .filter { task -> task.dueDate != null && requireNotNull(value = task.dueDate) < today }
            .maxByOrNull { task -> requireNotNull(value = task.dueDate) }
            ?.let(block = ::add)
        pending
            .filter { task -> task.dueDate == null || requireNotNull(value = task.dueDate) >= today }
            .minWithOrNull(
                comparator = compareBy<Task> { task ->
                    task.dueDate == null
                }.thenBy(selector = Task::dueDate),
            )
            ?.let(block = ::add)
        addAll(
            elements = candidates.filter { task ->
                task.isCompleted && requireNotNull(value = task.completedAt).toEpochMilliseconds() > completedSince
            }
        )
    }
        .distinctBy(selector = Task::key)
}
