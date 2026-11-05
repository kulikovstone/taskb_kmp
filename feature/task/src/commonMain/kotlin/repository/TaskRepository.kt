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
import com.mkulikov.taskb.feature.task.api.recurrence.recurrenceOrdinal
import com.mkulikov.taskb.feature.task.api.recurrence.nextRecurrenceDates
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

internal class TaskRepository(
    private val database: Lazy<AppDatabase>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ITaskRepository {
    private val dao get() = database.value.taskDao()
    private val records get() = flow { emitAll(dao.observeRecords()) }
    override val tasks = records.map { records -> records.map { it.task() } }
    override fun observeCalendar(from: LocalDate, through: LocalDate) = records.map { list -> list.flatMap { it.calendar(from, through) } }
    override fun observeTaskList(today: LocalDate) = records.map { list -> list.flatMap { it.taskList(today, (clock.now() - 24.hours).toEpochMilliseconds()) } }
    override suspend fun getById(id: Int): Task? = dao.getRecord(id)?.task()
    override suspend fun getOccurrence(id: Int, occurrence: TaskOccurrenceId): Task? {
        val record = dao.getRecord(id) ?: return null
        return record.schedules.find { it.id == occurrence.scheduleId }?.let { record.occurrence(it, occurrence.scheduledDate) }
    }

    override suspend fun save(task: Task): Boolean = saveOccurrence(task, TaskChangeScope.ThisAndFollowing)

    override suspend fun saveOccurrence(task: Task, scope: TaskChangeScope): Boolean = database.value.withWriteTransaction {
        val name = task.name.trim().also { require(it.isNotEmpty()) }
        require(task.recurrence == null || task.dueDate != null)
        require(task.recurrence?.endDate?.let { it >= requireNotNull(task.dueDate) } != false)
        if (task.id == 0) {
            val id = dao.insert(TaskEntity(name = name, dueDate = task.dueDate?.toEpochDays(), groupId = task.groupId,
                createdAt = clock.now().toEpochMilliseconds(), completedAt = task.completedAt?.toEpochMilliseconds(), recurring = task.recurrence != null,
                description = task.description)).toInt()
            task.recurrence?.let {
                require(nextRecurrenceDates(it, requireNotNull(task.dueDate), count = 1).isNotEmpty())
                dao.insertSchedule(it.entity(id, requireNotNull(task.dueDate)))
                dao.setCompletedAt(id, null)
            }
            return@withWriteTransaction id > 0
        }
        val record = dao.getRecord(task.id) ?: return@withWriteTransaction false
        val occurrence = task.occurrenceId
        val original = if (occurrence == null) record.task() else {
            val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return@withWriteTransaction false
            record.occurrence(schedule, occurrence.scheduledDate) ?: return@withWriteTransaction false
        }
        val changedSchedule = original.dueDate != task.dueDate || original.recurrence != task.recurrence
        if (!changedSchedule) return@withWriteTransaction dao.updateDetails(task.id, name, record.task.dueDate, task.groupId, task.description) > 0
        if (occurrence != null && original.recurrence == task.recurrence) {
            val date = task.dueDate ?: return@withWriteTransaction false
            val moved = if (scope == TaskChangeScope.ThisOccurrence) moveOne(record, occurrence, original.dueDate, date)
                else moveFollowing(record, occurrence, original.dueDate, date)
            if (moved) {
                val current = requireNotNull(dao.getById(task.id))
                dao.updateDetails(task.id, name, current.dueDate, task.groupId, task.description)
            }
            return@withWriteTransaction moved
        }
        if (!record.task.recurring && task.recurrence == null && occurrence == null) return@withWriteTransaction dao.updateDetails(task.id, name, task.dueDate?.toEpochDays(), task.groupId, task.description) > 0
        val oldDate = record.task.dueDate
        val lastCompleted = (record.occurrences.filter { it.completedAt != null }.map { maxOf(it.scheduledDate, it.movedDate ?: it.scheduledDate) } +
            listOfNotNull(oldDate.takeIf { record.task.completedAt != null })).maxOrNull()
        val today = clock.now().toLocalDateTime(timeZone()).date.toEpochDays()
        val boundary = maxOf(occurrence?.scheduledDate?.toEpochDays() ?: today, lastCompleted?.plus(1) ?: Long.MIN_VALUE)
        closeFrom(record, boundary)
        val date = task.dueDate?.let { LocalDate.fromEpochDays(maxOf(it.toEpochDays(), lastCompleted?.plus(1) ?: Long.MIN_VALUE)) }
        val newRule = task.recurrence
        if (newRule != null) {
            val start = requireNotNull(date)
            require(newRule.endDate?.let { it >= start } != false)
            require(nextRecurrenceDates(newRule, start, count = 1).isNotEmpty())
            dao.insertSchedule(newRule.entity(task.id, start))
        }
        // Preserve a completed one-off when it is converted into a recurring task.
        if (record.schedules.isEmpty() && record.task.completedAt != null && oldDate != null && task.recurrence != null) {
            val start = LocalDate.fromEpochDays(oldDate)
            val history = dao.insertSchedule(TaskRecurrence(RecurrenceFrequency.Daily, occurrenceCount = 1).entity(task.id, start).copy(closedBefore = oldDate + 1))
            dao.saveOccurrence(TaskOccurrenceEntity(history, oldDate, task.id, completedAt = record.task.completedAt))
        }
        dao.updateDetails(task.id, name, date?.toEpochDays(), task.groupId, task.description)
        dao.setRecurring(task.id, task.recurrence != null)
        dao.setCompletedAt(task.id, null)
        true
    }

    override suspend fun setCompleted(id: Int, completed: Boolean) {
        check(setOccurrenceCompleted(id, null, completed)) { "This task has changed. Reopen it and try again." }
    }

    override suspend fun setOccurrenceCompleted(id: Int, occurrence: TaskOccurrenceId?, completed: Boolean): Boolean = database.value.withWriteTransaction {
        val record = dao.getRecord(id) ?: return@withWriteTransaction false
        if (occurrence == null) {
            if (record.task.recurring) return@withWriteTransaction false
            dao.setCompletedAt(id, if (completed) completionTime(record.task.dueDate?.let(LocalDate::fromEpochDays)) else null)
        } else {
            val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return@withWriteTransaction false
            val event = record.occurrence(schedule, occurrence.scheduledDate) ?: return@withWriteTransaction false
            val previous = record.exception(occurrence)
            dao.saveOccurrence(previous.copy(completedAt = if (completed) previous.completedAt ?: completionTime(event.dueDate) else null))
            refreshSeriesCompletion(id)
        }
        true
    }

    override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean = database.value.withWriteTransaction {
        val record = dao.getRecord(id) ?: return@withWriteTransaction false
        if (record.task.recurring) return@withWriteTransaction false
        dao.reschedule(id, expectedDate?.toEpochDays(), date?.toEpochDays()) > 0
    }

    override suspend fun moveOccurrence(id: Int, occurrence: TaskOccurrenceId?, expectedDate: LocalDate?, date: LocalDate, scope: TaskChangeScope): Boolean = database.value.withWriteTransaction {
        val record = dao.getRecord(id) ?: return@withWriteTransaction false
        if (occurrence == null) return@withWriteTransaction !record.task.recurring && dao.reschedule(id, expectedDate?.toEpochDays(), date.toEpochDays()) > 0
        if (scope == TaskChangeScope.ThisOccurrence) return@withWriteTransaction moveOne(record, occurrence, expectedDate, date)
        moveFollowing(record, occurrence, expectedDate, date)
    }

    private suspend fun moveFollowing(record: TaskRecord, occurrence: TaskOccurrenceId, expectedDate: LocalDate?, date: LocalDate): Boolean {
        val id = record.task.id
        val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return false
        val event = record.occurrence(schedule, occurrence.scheduledDate) ?: return false
        if (event.dueDate != expectedDate) return false
        val completed = record.occurrences.filter { it.completedAt != null }
        val historyEnd = completed.maxOfOrNull { maxOf(it.scheduledDate, it.movedDate ?: it.scheduledDate) }
        if (historyEnd != null && date.toEpochDays() <= historyEnd) return false
        val boundary = maxOf(occurrence.scheduledDate.toEpochDays(), historyEnd?.plus(1) ?: Long.MIN_VALUE)
        val rule = schedule.rule()
        val next = nextRecurrenceDates(rule, LocalDate.fromEpochDays(schedule.startDate), LocalDate.fromEpochDays(boundary), 1).firstOrNull() ?: return false
        val newDate = date
        val delta = newDate.toEpochDays() - next.toEpochDays()
        val before = requireNotNull(recurrenceOrdinal(rule, LocalDate.fromEpochDays(schedule.startDate), next)).toInt()
        val remaining = rule.occurrenceCount?.minus(before)
        if (remaining != null && remaining <= 0) return false
        val shifted = rule.copy(
            weekdays = rule.weekdays.map { DayOfWeek.entries[((it.ordinal.toLong() + delta) % 7 + 7).toInt() % 7] }.toSet(),
            dayOfMonth = if (rule.frequency == RecurrenceFrequency.Monthly || rule.frequency == RecurrenceFrequency.Yearly) newDate.day else rule.dayOfMonth,
            month = if (rule.frequency == RecurrenceFrequency.Yearly) newDate.month.ordinal + 1 else rule.month,
            endDate = rule.endDate?.plus(delta, DateTimeUnit.DAY), occurrenceCount = remaining,
        )
        closeFrom(record, boundary)
        dao.insertSchedule(shifted.entity(id, newDate))
        dao.updateDetails(id, record.task.name, newDate.toEpochDays(), record.task.groupId, record.task.description)
        dao.setRecurring(id, true)
        dao.setCompletedAt(id, null)
        return true
    }

    override suspend fun deleteOccurrence(id: Int, occurrence: TaskOccurrenceId?, scope: TaskChangeScope): Boolean = database.value.withWriteTransaction {
        val record = dao.getRecord(id) ?: return@withWriteTransaction false
        if (occurrence == null || scope == TaskChangeScope.EntireTask) { dao.deleteById(id); return@withWriteTransaction true }
        val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return@withWriteTransaction false
        if (record.occurrence(schedule, occurrence.scheduledDate) == null) return@withWriteTransaction false
        if (scope == TaskChangeScope.ThisOccurrence) dao.saveOccurrence(record.exception(occurrence).copy(skipped = true, completedAt = null))
        else closeFrom(record, occurrence.scheduledDate.toEpochDays())
        refreshSeriesCompletion(id)
        true
    }

    override suspend fun delete(id: Int) = dao.deleteById(id)

    private fun completionTime(date: LocalDate?): Long {
        val now = clock.now()
        val zone = timeZone()
        return if (date != null && date < now.toLocalDateTime(zone).date) {
            date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1
        } else now.toEpochMilliseconds()
    }

    private suspend fun refreshSeriesCompletion(id: Int) {
        val record = dao.getRecord(id) ?: return
        if (!record.task.recurring) return
        val today = clock.now().toLocalDateTime(timeZone()).date
        val pending = record.taskList(today, Long.MAX_VALUE).any { !it.isCompleted }
        dao.setCompletedAt(id, if (pending) null else record.task.completedAt
            ?: record.occurrences.mapNotNull { it.completedAt }.maxOrNull() ?: clock.now().toEpochMilliseconds())
    }

    private fun TaskRecord.exception(id: TaskOccurrenceId) = occurrences.find { it.scheduleId == id.scheduleId && it.scheduledDate == id.scheduledDate.toEpochDays() }
        ?: TaskOccurrenceEntity(id.scheduleId, id.scheduledDate.toEpochDays(), task.id)

    private suspend fun moveOne(record: TaskRecord, occurrence: TaskOccurrenceId, expectedDate: LocalDate?, date: LocalDate): Boolean {
        val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return false
        val event = record.occurrence(schedule, occurrence.scheduledDate) ?: return false
        if (event.dueDate != expectedDate) return false
        val previous = record.exception(occurrence)
        dao.saveOccurrence(previous.copy(
            movedDate = date.toEpochDays().takeUnless { it == occurrence.scheduledDate.toEpochDays() },
        ))
        return true
    }

    private suspend fun closeFrom(record: TaskRecord, boundary: Long) {
        for (schedule in record.schedules) if (schedule.closedBefore?.let { it > boundary } != false) {
            dao.closeSchedule(schedule.id, boundary)
            dao.deleteOccurrencesFrom(schedule.id, boundary)
        }
    }
}
