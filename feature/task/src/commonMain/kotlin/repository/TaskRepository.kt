package com.mkulikov.taskb.feature.task.repository

import androidx.room3.withWriteTransaction
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.feature.task.api.database.TaskOccurrenceEntity
import com.mkulikov.taskb.feature.task.api.database.TaskRecord
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.recurrence.nextRecurrenceDates
import com.mkulikov.taskb.feature.task.api.recurrence.recurrenceOrdinal
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

internal class TaskRepository(
    private val database: Lazy<AppDatabase>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ITaskRepository {
    private val dao
        get() = database.value.taskDao()

    private val records
        get() = flow { emitAll(flow = dao.observeRecords()) }

    override val tasks = records.map { records -> records.map { record -> record.task() } }

    override fun observeCalendar(from: LocalDate, through: LocalDate): Flow<List<Task>> {
        return records.map { list ->
            list.flatMap { record -> record.calendar(from = from, through = through) }
        }
    }

    override fun observeTaskList(today: LocalDate): Flow<List<Task>> {
        return records.map { list ->
            list.flatMap { record -> record.taskList(
                today = today,
                completedSince = (clock.now() - 24.hours).toEpochMilliseconds(),
            ) }
        }
    }

    override suspend fun getById(id: Int): Task? = dao.getRecord(id = id)?.task()

    override suspend fun getOccurrence(id: Int, occurrence: TaskOccurrenceId): Task? {
        val record = dao.getRecord(id = id) ?: return null
        return record.schedules
            .find { schedule -> schedule.id == occurrence.scheduleId }
            ?.let { schedule -> record.occurrence(schedule = schedule, date = occurrence.scheduledDate) }
    }

    override suspend fun save(task: Task): Boolean {
        return saveOccurrence(
            task = task,
            scope = TaskChangeScope.ThisAndFollowing,
        )
    }

    override suspend fun saveOccurrence(task: Task, scope: TaskChangeScope): Boolean {
        return database.value.withWriteTransaction {
            val name = task.name.trim().also { trimmedName -> require(value = trimmedName.isNotEmpty()) }
            require(value = task.recurrence == null || task.dueDate != null)
            require(
                value = task.recurrence?.endDate?.let { date ->
                    date >= requireNotNull(value = task.dueDate)
                } != false,
            )
            if (task.id == 0) {
                val id = dao.insert(
                        task = TaskEntity(
                            name = name,
                            dueDate = task.dueDate?.toEpochDays(),
                            groupId = task.groupId,
                            createdAt = clock.now().toEpochMilliseconds(),
                            completedAt = task.completedAt?.toEpochMilliseconds(),
                            recurring = task.recurrence != null,
                            description = task.description,
                        )
                    )
                    .toInt()
                task.recurrence?.let { recurrence ->
                    require(
                        value = nextRecurrenceDates(
                            rule = recurrence,
                            start = requireNotNull(value = task.dueDate),
                            count = 1,
                        )
                            .isNotEmpty()
                    )
                    dao.insertSchedule(
                        schedule = recurrence.entity(taskId = id, start = requireNotNull(value = task.dueDate))
                    )
                    dao.setCompletedAt(id = id, completedAt = null)
                }
                return@withWriteTransaction id > 0
            }
            val record = dao.getRecord(id = task.id) ?: return@withWriteTransaction false
            val occurrence = task.occurrenceId
            val original = if (occurrence == null) {
                record.task()
            } else {
                val schedule = record.schedules.find { schedule -> schedule.id == occurrence.scheduleId }
                    ?: return@withWriteTransaction false
                record.occurrence(schedule = schedule, date = occurrence.scheduledDate)
                    ?: return@withWriteTransaction false
            }
            val changedSchedule = original.dueDate != task.dueDate || original.recurrence != task.recurrence
            if (!changedSchedule)
                return@withWriteTransaction dao.updateDetails(
                    id = task.id,
                    name = name,
                    dueDate = record.task.dueDate,
                    groupId = task.groupId,
                    description = task.description,
                ) > 0
            if (occurrence != null && original.recurrence == task.recurrence) {
                val date = task.dueDate ?: return@withWriteTransaction false
                val moved = if (scope == TaskChangeScope.ThisOccurrence) {
                    moveOne(
                        record = record,
                        occurrence = occurrence,
                        expectedDate = original.dueDate,
                        date = date,
                    )
                } else {
                    moveFollowing(
                        record = record,
                        occurrence = occurrence,
                        expectedDate = original.dueDate,
                        date = date,
                    )
                }
                if (moved) {
                    val current = requireNotNull(value = dao.getById(id = task.id))
                    dao.updateDetails(
                        id = task.id,
                        name = name,
                        dueDate = current.dueDate,
                        groupId = task.groupId,
                        description = task.description,
                    )
                }
                return@withWriteTransaction moved
            }
            if (!record.task.recurring && task.recurrence == null && occurrence == null)
                return@withWriteTransaction dao.updateDetails(
                    id = task.id,
                    name = name,
                    dueDate = task.dueDate?.toEpochDays(),
                    groupId = task.groupId,
                    description = task.description,
                ) > 0
            val oldDate = record.task.dueDate
            val lastCompleted = (record.occurrences
                    .filter { occurrence -> occurrence.completedAt != null }
                    .map { occurrence -> maxOf(
                        a = occurrence.scheduledDate,
                        b = occurrence.movedDate ?: occurrence.scheduledDate,
                    ) } +
                    listOfNotNull(element = oldDate.takeIf { record.task.completedAt != null }))
                .maxOrNull()
            val today = clock.now().toLocalDateTime(timeZone = timeZone()).date.toEpochDays()
            val boundary = maxOf(
                a = occurrence?.scheduledDate?.toEpochDays() ?: today,
                b = lastCompleted?.plus(other = 1) ?: Long.MIN_VALUE,
            )
            closeFrom(record = record, boundary = boundary)
            val date = task.dueDate?.let { date ->
                LocalDate.fromEpochDays(
                    epochDays = maxOf(a = date.toEpochDays(), b = lastCompleted?.plus(other = 1) ?: Long.MIN_VALUE)
                )
            }
            val newRule = task.recurrence
            if (newRule != null) {
                val start = requireNotNull(value = date)
                require(value = newRule.endDate?.let { date -> date >= start } != false)
                require(value = nextRecurrenceDates(rule = newRule, start = start, count = 1).isNotEmpty())
                dao.insertSchedule(schedule = newRule.entity(taskId = task.id, start = start))
            }
            // Preserve a completed one-off when it is converted into a recurring task.
            if (
                record.schedules.isEmpty() &&
                    record.task.completedAt != null &&
                    oldDate != null &&
                    task.recurrence != null
            ) {
                val start = LocalDate.fromEpochDays(epochDays = oldDate)
                val history = dao.insertSchedule(
                    schedule = TaskRecurrence(frequency = RecurrenceFrequency.Daily, occurrenceCount = 1)
                        .entity(taskId = task.id, start = start)
                        .copy(closedBefore = oldDate + 1)
                )
                dao.saveOccurrence(
                    occurrence = TaskOccurrenceEntity(
                        scheduleId = history,
                        scheduledDate = oldDate,
                        taskId = task.id,
                        completedAt = record.task.completedAt,
                    )
                )
            }
            dao.updateDetails(
                id = task.id,
                name = name,
                dueDate = date?.toEpochDays(),
                groupId = task.groupId,
                description = task.description,
            )
            dao.setRecurring(id = task.id, recurring = task.recurrence != null)
            dao.setCompletedAt(id = task.id, completedAt = null)
            true
        }
    }

    override suspend fun setCompleted(id: Int, completed: Boolean) {
        check(value = setOccurrenceCompleted(id = id, occurrence = null, completed = completed)) {
            "This task has changed. Reopen it and try again."
        }
    }

    override suspend fun setOccurrenceCompleted(
        id: Int,
        occurrence: TaskOccurrenceId?,
        completed: Boolean,
    ): Boolean {
        return database.value.withWriteTransaction {
            val record = dao.getRecord(id = id) ?: return@withWriteTransaction false
            if (occurrence == null) {
                if (record.task.recurring) return@withWriteTransaction false
                dao.setCompletedAt(
                    id = id,
                    completedAt = if (completed) {
                        completionTime(date = record.task.dueDate?.let(block = LocalDate::fromEpochDays))
                    } else {
                        null
                    },
                )
            } else {
                val schedule = record.schedules.find { schedule -> schedule.id == occurrence.scheduleId }
                    ?: return@withWriteTransaction false
                val event = record.occurrence(schedule = schedule, date = occurrence.scheduledDate)
                    ?: return@withWriteTransaction false
                val previous = record.exception(id = occurrence)
                dao.saveOccurrence(
                    occurrence = previous.copy(
                        completedAt = if (completed) {
                            previous.completedAt ?: completionTime(date = event.dueDate)
                        } else {
                            null
                        },
                    )
                )
                refreshSeriesCompletion(id = id)
            }
            true
        }
    }

    override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean {
        return database.value.withWriteTransaction {
            val record = dao.getRecord(id = id) ?: return@withWriteTransaction false
            if (record.task.recurring) return@withWriteTransaction false
            dao.reschedule(id = id, expectedDate = expectedDate?.toEpochDays(), dueDate = date?.toEpochDays()) > 0
        }
    }

    override suspend fun moveOccurrence(
        id: Int,
        occurrence: TaskOccurrenceId?,
        expectedDate: LocalDate?,
        date: LocalDate,
        scope: TaskChangeScope,
    ): Boolean {
        return database.value.withWriteTransaction {
            val record = dao.getRecord(id = id) ?: return@withWriteTransaction false
            if (occurrence == null)
                return@withWriteTransaction !record.task.recurring &&
                    dao.reschedule(
                        id = id,
                        expectedDate = expectedDate?.toEpochDays(),
                        dueDate = date.toEpochDays(),
                    ) > 0
            if (scope == TaskChangeScope.ThisOccurrence)
                return@withWriteTransaction moveOne(
                    record = record,
                    occurrence = occurrence,
                    expectedDate = expectedDate,
                    date = date,
                )
            moveFollowing(record = record, occurrence = occurrence, expectedDate = expectedDate, date = date)
        }
    }

    private suspend fun moveFollowing(
        record: TaskRecord,
        occurrence: TaskOccurrenceId,
        expectedDate: LocalDate?,
        date: LocalDate,
    ): Boolean {
        val id = record.task.id
        val schedule = record.schedules.find { schedule -> schedule.id == occurrence.scheduleId } ?: return false
        val event = record.occurrence(schedule = schedule, date = occurrence.scheduledDate) ?: return false
        if (event.dueDate != expectedDate) return false
        val completed = record.occurrences.filter { occurrence -> occurrence.completedAt != null }
        val historyEnd = completed.maxOfOrNull { occurrence -> maxOf(
            a = occurrence.scheduledDate,
            b = occurrence.movedDate ?: occurrence.scheduledDate,
        ) }
        if (historyEnd != null && date.toEpochDays() <= historyEnd) return false
        val boundary = maxOf(
            a = occurrence.scheduledDate.toEpochDays(),
            b = historyEnd?.plus(other = 1) ?: Long.MIN_VALUE,
        )
        val rule = schedule.rule()
        val next = nextRecurrenceDates(
                rule = rule,
                start = LocalDate.fromEpochDays(epochDays = schedule.startDate),
                from = LocalDate.fromEpochDays(epochDays = boundary),
                count = 1,
            )
            .firstOrNull() ?: return false
        val newDate = date
        val delta = newDate.toEpochDays() - next.toEpochDays()
        val before = requireNotNull(
                value = recurrenceOrdinal(
                    rule = rule,
                    start = LocalDate.fromEpochDays(epochDays = schedule.startDate),
                    date = next,
                )
            )
            .toInt()
        val remaining = rule.occurrenceCount?.minus(other = before)
        if (remaining != null && remaining <= 0) return false
        val shifted = rule.copy(
            weekdays = rule.weekdays
                .map { weekday -> DayOfWeek.entries[((weekday.ordinal.toLong() + delta) % 7 + 7).toInt() % 7] }
                .toSet(),
            dayOfMonth = if (
                rule.frequency == RecurrenceFrequency.Monthly ||
                    rule.frequency == RecurrenceFrequency.Yearly
            ) {
                newDate.day
            } else {
                rule.dayOfMonth
            },
            month = if (rule.frequency == RecurrenceFrequency.Yearly) newDate.month.ordinal + 1 else rule.month,
            endDate = rule.endDate?.plus(value = delta, unit = DateTimeUnit.DAY),
            occurrenceCount = remaining,
        )
        closeFrom(record = record, boundary = boundary)
        dao.insertSchedule(schedule = shifted.entity(taskId = id, start = newDate))
        dao.updateDetails(
            id = id,
            name = record.task.name,
            dueDate = newDate.toEpochDays(),
            groupId = record.task.groupId,
            description = record.task.description,
        )
        dao.setRecurring(id = id, recurring = true)
        dao.setCompletedAt(id = id, completedAt = null)
        return true
    }

    override suspend fun deleteOccurrence(id: Int, occurrence: TaskOccurrenceId?, scope: TaskChangeScope): Boolean {
        return database.value.withWriteTransaction {
            val record = dao.getRecord(id = id) ?: return@withWriteTransaction false
            if (occurrence == null || scope == TaskChangeScope.EntireTask) {
                dao.deleteById(id = id)
                return@withWriteTransaction true
            }
            val schedule = record.schedules.find { schedule ->
                schedule.id == occurrence.scheduleId
            } ?: return@withWriteTransaction false
            if (record.occurrence(schedule = schedule, date = occurrence.scheduledDate) == null)
                return@withWriteTransaction false
            if (scope == TaskChangeScope.ThisOccurrence) {
                dao.saveOccurrence(
                    occurrence = record.exception(id = occurrence).copy(skipped = true, completedAt = null)
                )
            } else {
                closeFrom(record = record, boundary = occurrence.scheduledDate.toEpochDays())
            }
            refreshSeriesCompletion(id = id)
            true
        }
    }

    override suspend fun delete(id: Int) = dao.deleteById(id = id)

    private fun completionTime(date: LocalDate?): Long {
        val now = clock.now()
        val zone = timeZone()
        return if (date != null && date < now.toLocalDateTime(timeZone = zone).date) {
            date.plus(value = 1, unit = DateTimeUnit.DAY).atStartOfDayIn(timeZone = zone).toEpochMilliseconds() - 1
        } else {
            now.toEpochMilliseconds()
        }
    }

    private suspend fun refreshSeriesCompletion(id: Int) {
        val record = dao.getRecord(id = id) ?: return
        if (!record.task.recurring) return
        val today = clock.now().toLocalDateTime(timeZone = timeZone()).date
        val pending = record.taskList(today = today, completedSince = Long.MAX_VALUE).any { task -> !task.isCompleted }
        dao.setCompletedAt(
            id = id,
            completedAt = if (pending) {
                null
            } else {
                record.task.completedAt
                    ?: record.occurrences.mapNotNull(transform = TaskOccurrenceEntity::completedAt).maxOrNull()
                    ?: clock.now().toEpochMilliseconds()
            },
        )
    }

    private fun TaskRecord.exception(id: TaskOccurrenceId): TaskOccurrenceEntity {
        return occurrences.find { occurrence ->
            occurrence.scheduleId == id.scheduleId && occurrence.scheduledDate == id.scheduledDate.toEpochDays()
        }
            ?: TaskOccurrenceEntity(
                scheduleId = id.scheduleId,
                scheduledDate = id.scheduledDate.toEpochDays(),
                taskId = task.id,
            )
    }

    private suspend fun moveOne(
        record: TaskRecord,
        occurrence: TaskOccurrenceId,
        expectedDate: LocalDate?,
        date: LocalDate,
    ): Boolean {
        val schedule = record.schedules.find { schedule -> schedule.id == occurrence.scheduleId } ?: return false
        val event = record.occurrence(schedule = schedule, date = occurrence.scheduledDate) ?: return false
        if (event.dueDate != expectedDate) return false
        val previous = record.exception(id = occurrence)
        dao.saveOccurrence(
            occurrence = previous.copy(
                movedDate = date.toEpochDays().takeUnless { epochDay ->
                    epochDay == occurrence.scheduledDate.toEpochDays()
                }
            )
        )
        return true
    }

    private suspend fun closeFrom(record: TaskRecord, boundary: Long) {
        for (schedule in record.schedules) if (schedule.closedBefore?.let { closedBefore ->
            closedBefore > boundary
        } != false) {
            dao.closeSchedule(id = schedule.id, before = boundary)
            dao.deleteOccurrencesFrom(scheduleId = schedule.id, fromDate = boundary)
        }
    }
}
