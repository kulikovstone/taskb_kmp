package com.mkulikov.taskb.feature.event.repository

import androidx.room3.withWriteTransaction
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.event.api.database.EventEntity
import com.mkulikov.taskb.feature.event.api.database.EventOccurrenceEntity
import com.mkulikov.taskb.feature.event.api.database.EventRecord
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.feature.task.api.recurrence.recurrenceOrdinal
import com.mkulikov.taskb.feature.task.api.recurrence.nextRecurrenceDates
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

internal class EventRepository(
    private val database: Lazy<AppDatabase>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : IEventRepository {
    private val dao get() = database.value.eventDao()
    private val records get() = flow { emitAll(dao.observeRecords()) }
    override val events = records.map { records -> records.map { it.event() } }
    override fun observeCalendar(from: LocalDate, through: LocalDate) = records.map { list -> list.flatMap { it.calendar(from, through) } }
    override suspend fun getById(id: Int): Event? = dao.getRecord(id)?.event()
    override suspend fun getOccurrence(id: Int, occurrence: EventOccurrenceId): Event? {
        val record = dao.getRecord(id) ?: return null
        return record.schedules.find { it.id == occurrence.scheduleId }?.let { record.occurrence(it, occurrence.scheduledDate) }
    }

    override suspend fun save(event: Event): Boolean = saveOccurrence(event, EventChangeScope.ThisAndFollowing)

    override suspend fun saveOccurrence(event: Event, scope: EventChangeScope): Boolean =
        saveWithDuration(event.copy(durationMinutes = event.durationMinutes ?: event.startTime?.let { if (event.id == 0) 30 else 60 },
            durationDays = if (event.startTime == null) event.durationDays else 1), scope)

    private suspend fun saveWithDuration(event: Event, scope: EventChangeScope): Boolean = database.value.withWriteTransaction {
        val name = event.name.trim().also { require(it.isNotEmpty()) }
        require(event.durationDays > 0) { "An event must occupy at least one day." }
        require(event.recurrence == null || event.dueDate != null)
        require(event.startTime == null || event.dueDate != null) { "Choose a date before setting a start time." }
        require(event.durationMinutes?.let { event.startTime != null && it > 0 } != false) { "A duration requires a start time and must be positive." }
        require(event.startTime?.let { it.second == 0 && it.nanosecond == 0 } != false) { "Event times use minute precision." }
        require(runCatching { event.endDate }.isSuccess) { "The end date is outside the supported range." }
        require(event.recurrence?.endDate?.let { it >= requireNotNull(event.dueDate) } != false)
        if (event.id == 0) {
            val id = dao.insert(EventEntity(name = name, dueDate = event.dueDate?.toEpochDays(), groupId = event.groupId,
                createdAt = clock.now().toEpochMilliseconds(), recurring = event.recurrence != null,
                description = event.description, startTimeMinutes = event.startTime?.minuteOfDay(), durationMinutes = event.durationMinutes, durationDays = event.durationDays)).toInt()
            event.recurrence?.let {
                require(nextRecurrenceDates(it, requireNotNull(event.dueDate), count = 1).isNotEmpty())
                dao.insertSchedule(it.entity(id, requireNotNull(event.dueDate), event.startTime, event.durationMinutes, event.durationDays))
            }
            return@withWriteTransaction id > 0
        }
        val record = dao.getRecord(event.id) ?: return@withWriteTransaction false
        val occurrence = event.occurrenceId
        val original = if (occurrence == null) record.event() else {
            val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return@withWriteTransaction false
            record.occurrence(schedule, occurrence.scheduledDate) ?: return@withWriteTransaction false
        }
        val changedTiming = original.startTime != event.startTime || original.durationMinutes != event.durationMinutes || original.durationDays != event.durationDays
        val changedSchedule = original.dueDate != event.dueDate || original.recurrence != event.recurrence || changedTiming
        if (!changedSchedule) return@withWriteTransaction dao.updateDetails(event.id, name, record.event.dueDate, event.groupId, event.description,
            record.event.startTimeMinutes, normalizedEventDuration(record.event.startTimeMinutes, record.event.durationMinutes), record.event.durationDays) > 0
        if (occurrence != null && original.recurrence == event.recurrence) {
            val date = event.dueDate ?: return@withWriteTransaction false
            val timing = event.takeIf { changedTiming }
            val moved = if (scope == EventChangeScope.ThisOccurrence) moveOne(record, occurrence, original.dueDate, date, timing)
                else moveFollowing(record, occurrence, original.dueDate, date, timing)
            if (moved) {
                val current = requireNotNull(dao.getById(event.id))
                dao.updateDetails(event.id, name, current.dueDate, event.groupId, event.description, current.startTimeMinutes,
                    normalizedEventDuration(current.startTimeMinutes, current.durationMinutes), current.durationDays)
            }
            return@withWriteTransaction moved
        }
        if (!record.event.recurring && event.recurrence == null && occurrence == null) return@withWriteTransaction dao.updateDetails(event.id, name, event.dueDate?.toEpochDays(), event.groupId, event.description,
            event.startTime?.minuteOfDay(), event.durationMinutes, event.durationDays) > 0
        val today = clock.now().toLocalDateTime(timeZone()).date.toEpochDays()
        val boundary = occurrence?.scheduledDate?.toEpochDays() ?: today
        closeFrom(record, boundary)
        val date = event.dueDate
        val newRule = event.recurrence
        if (newRule != null) {
            val start = requireNotNull(date)
            require(newRule.endDate?.let { it >= start } != false)
            require(nextRecurrenceDates(newRule, start, count = 1).isNotEmpty())
            dao.insertSchedule(newRule.entity(event.id, start, event.startTime, event.durationMinutes, event.durationDays))
        }
        dao.updateDetails(event.id, name, date?.toEpochDays(), event.groupId, event.description, event.startTime?.minuteOfDay(), event.durationMinutes, event.durationDays)
        dao.setRecurring(event.id, event.recurrence != null)
        true
    }

    override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean = database.value.withWriteTransaction {
        val record = dao.getRecord(id) ?: return@withWriteTransaction false
        if (record.event.recurring) return@withWriteTransaction false
        dao.reschedule(id, expectedDate?.toEpochDays(), date?.toEpochDays()) > 0
    }

    override suspend fun moveOccurrence(id: Int, occurrence: EventOccurrenceId?, expectedDate: LocalDate?, date: LocalDate, scope: EventChangeScope): Boolean = database.value.withWriteTransaction {
        val record = dao.getRecord(id) ?: return@withWriteTransaction false
        if (occurrence == null) return@withWriteTransaction !record.event.recurring && dao.reschedule(id, expectedDate?.toEpochDays(), date.toEpochDays()) > 0
        if (scope == EventChangeScope.ThisOccurrence) return@withWriteTransaction moveOne(record, occurrence, expectedDate, date)
        moveFollowing(record, occurrence, expectedDate, date)
    }

    private suspend fun moveFollowing(record: EventRecord, occurrence: EventOccurrenceId, expectedDate: LocalDate?, date: LocalDate, timing: Event? = null): Boolean {
        val id = record.event.id
        val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return false
        val event = record.occurrence(schedule, occurrence.scheduledDate) ?: return false
        if (event.dueDate != expectedDate) return false
        val boundary = occurrence.scheduledDate.toEpochDays()
        val rule = schedule.rule()
        val next = nextRecurrenceDates(rule, LocalDate.fromEpochDays(schedule.startDate), LocalDate.fromEpochDays(boundary), 1).firstOrNull() ?: return false
        val newDate = if (timing != null && date == expectedDate) maxOf(date, next) else date
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
        val time = if (timing == null) event.startTime else timing.startTime
        val duration = if (timing == null) event.durationMinutes else timing.durationMinutes
        val days = timing?.durationDays ?: event.durationDays
        dao.insertSchedule(shifted.entity(id, newDate, time, duration, days))
        dao.updateDetails(id, record.event.name, newDate.toEpochDays(), record.event.groupId, record.event.description, time?.minuteOfDay(), duration, days)
        dao.setRecurring(id, true)
        return true
    }

    override suspend fun deleteOccurrence(id: Int, occurrence: EventOccurrenceId?, scope: EventChangeScope): Boolean = database.value.withWriteTransaction {
        val record = dao.getRecord(id) ?: return@withWriteTransaction false
        if (occurrence == null || scope == EventChangeScope.EntireEvent) { dao.deleteById(id); return@withWriteTransaction true }
        val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return@withWriteTransaction false
        if (record.occurrence(schedule, occurrence.scheduledDate) == null) return@withWriteTransaction false
        if (scope == EventChangeScope.ThisOccurrence) dao.saveOccurrence(record.exception(occurrence).copy(skipped = true))
        else closeFrom(record, occurrence.scheduledDate.toEpochDays())
        true
    }

    override suspend fun delete(id: Int) = dao.deleteById(id)

    private fun EventRecord.exception(id: EventOccurrenceId) = occurrences.find { it.scheduleId == id.scheduleId && it.scheduledDate == id.scheduledDate.toEpochDays() }
        ?: EventOccurrenceEntity(id.scheduleId, id.scheduledDate.toEpochDays(), event.id)

    private suspend fun moveOne(record: EventRecord, occurrence: EventOccurrenceId, expectedDate: LocalDate?, date: LocalDate, timing: Event? = null): Boolean {
        val schedule = record.schedules.find { it.id == occurrence.scheduleId } ?: return false
        val event = record.occurrence(schedule, occurrence.scheduledDate) ?: return false
        if (event.dueDate != expectedDate) return false
        val previous = record.exception(occurrence)
        dao.saveOccurrence(previous.copy(
            movedDate = date.toEpochDays().takeUnless { it == occurrence.scheduledDate.toEpochDays() },
            overridesTiming = timing != null || previous.overridesTiming,
            startTimeMinutes = if (timing == null) previous.startTimeMinutes else timing.startTime?.minuteOfDay(),
            durationMinutes = if (timing == null) normalizedEventDuration(previous.startTimeMinutes, previous.durationMinutes) else timing.durationMinutes,
            durationDays = timing?.durationDays ?: previous.durationDays,
        ))
        return true
    }

    private suspend fun closeFrom(record: EventRecord, boundary: Long) {
        for (schedule in record.schedules) if (schedule.closedBefore?.let { it > boundary } != false) {
            dao.closeSchedule(schedule.id, boundary)
            dao.deleteOccurrencesFrom(schedule.id, boundary)
        }
    }
}
