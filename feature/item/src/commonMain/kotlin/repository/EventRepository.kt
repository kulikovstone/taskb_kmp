package com.mkulikov.taskb.feature.item.repository

import androidx.room3.withWriteTransaction
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.item.api.database.EventEntity
import com.mkulikov.taskb.feature.item.api.database.EventOccurrenceEntity
import com.mkulikov.taskb.feature.item.api.database.EventRecord
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.item.api.recurrence.nextRecurrenceDates
import com.mkulikov.taskb.feature.item.api.recurrence.recurrenceOrdinal
import com.mkulikov.taskb.feature.item.api.repository.IEventRepository
import com.mkulikov.taskb.feature.settings.api.database.rememberCreatedItemGroup
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

internal class EventRepository(
    private val database: Lazy<AppDatabase>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : IEventRepository {
    private val dao
        get() = database.value.eventDao()

    private val records
        get() = flow { emitAll(flow = dao.observeRecords()) }

    override val events = records.map { records -> records.map { record -> record.event() } }

    override fun observeCalendar(from: LocalDate, through: LocalDate): Flow<List<Event>> {
        return records.map { list ->
            list.flatMap { record -> record.calendar(from = from, through = through) }
        }
    }

    override suspend fun getById(id: Int): Event? = dao.getRecord(id = id)?.event()

    override suspend fun getOccurrence(id: Int, occurrence: ItemOccurrenceId): Event? {
        return dao.getRecord(id = id)?.occurrence(id = occurrence)
    }

    override suspend fun save(event: Event): Boolean {
        return saveOccurrence(
            event = event,
            scope = ItemChangeScope.ThisAndFollowing,
        )
    }

    override suspend fun saveOccurrence(event: Event, scope: ItemChangeScope): Boolean {
        return saveWithDuration(draft = event, scope = scope)
    }

    private suspend fun saveWithDuration(draft: Event, scope: ItemChangeScope): Boolean {
        return database.value.withWriteTransaction {
            val record = if (draft.id == 0) null else dao.getRecord(id = draft.id) ?: return@withWriteTransaction false
            val occurrence = draft.occurrenceId
            val original = record?.let { stored ->
                if (occurrence == null) {
                    stored.event()
                } else {
                    stored.occurrence(id = occurrence) ?: return@withWriteTransaction false
                }
            }
            val event = draft.copy(
                durationMinutes = draft.durationMinutes ?: draft.startTime?.let {
                    original?.durationMinutes ?: Event.DEFAULT_DURATION_MINUTES
                },
                durationDays = if (draft.startTime == null) draft.durationDays else 1,
            )
            val name = event.name.trim().also { trimmedName -> require(value = trimmedName.isNotEmpty()) }
            require(value = event.durationDays > 0) { "An event must occupy at least one day." }
            require(value = event.recurrence == null || event.dueDate != null)
            require(value = event.startTime == null || event.dueDate != null) {
                "Choose a date before setting a start time."
            }
            require(value = event.durationMinutes?.let { minutes -> event.startTime != null && minutes > 0 } != false) {
                "A duration requires a start time and must be positive."
            }
            require(value = event.startTime?.let { time -> time.second == 0 && time.nanosecond == 0 } != false) {
                "Event times use minute precision."
            }
            require(value = runCatching { event.endDate }.isSuccess) {
                "The end date is outside the supported range."
            }
            require(
                value = event.recurrence?.endDate?.let { date ->
                    date >= requireNotNull(value = event.dueDate)
                } != false,
            )
            if (record == null) {
                val id = dao.insert(
                        event = EventEntity(
                            name = name,
                            dueDate = event.dueDate?.toEpochDays(),
                            groupId = event.groupId,
                            createdAt = clock.now().toEpochMilliseconds(),
                            recurring = event.recurrence != null,
                            description = event.description,
                            startTimeMinutes = event.startTime?.minuteOfDay(),
                            durationMinutes = event.durationMinutes,
                            durationDays = event.durationDays,
                        )
                    )
                    .toInt()
                event.recurrence?.let { recurrence ->
                    require(
                        value = nextRecurrenceDates(
                            rule = recurrence,
                            start = requireNotNull(value = event.dueDate),
                            count = 1,
                        )
                            .isNotEmpty()
                    )
                    dao.insertSchedule(
                        schedule = recurrence.entity(
                            eventId = id,
                            start = requireNotNull(value = event.dueDate),
                            startTime = event.startTime,
                            durationMinutes = event.durationMinutes,
                            durationDays = event.durationDays,
                        )
                    )
                }
                if (id > 0) database.value.settingsDao().rememberCreatedItemGroup(groupId = event.groupId)
                return@withWriteTransaction id > 0
            }
            checkNotNull(value = original)
            val changedTiming = original.startTime != event.startTime ||
                original.durationMinutes != event.durationMinutes ||
                original.durationDays != event.durationDays
            val changedSchedule = original.dueDate != event.dueDate || original.recurrence != event.recurrence ||
                changedTiming
            if (!changedSchedule)
                return@withWriteTransaction dao.updateDetails(
                    id = event.id,
                    name = name,
                    dueDate = record.event.dueDate,
                    groupId = event.groupId,
                    description = event.description,
                    startTimeMinutes = record.event.startTimeMinutes,
                    durationMinutes = storedEventDuration(
                        startTimeMinutes = record.event.startTimeMinutes,
                        durationMinutes = record.event.durationMinutes,
                    ),
                    durationDays = record.event.durationDays,
                ) > 0
            if (occurrence != null && original.recurrence == event.recurrence) {
                val date = event.dueDate ?: return@withWriteTransaction false
                val timing = event.takeIf { changedTiming }
                val moved = if (scope == ItemChangeScope.ThisOccurrence) {
                    moveOne(
                        record = record,
                        occurrence = occurrence,
                        expectedDate = original.dueDate,
                        date = date,
                        timing = timing,
                    )
                } else {
                    moveFollowing(
                        record = record,
                        occurrence = occurrence,
                        expectedDate = original.dueDate,
                        date = date,
                        timing = timing,
                    )
                }
                if (moved) {
                    val current = requireNotNull(value = dao.getById(id = event.id))
                    dao.updateDetails(
                        id = event.id,
                        name = name,
                        dueDate = current.dueDate,
                        groupId = event.groupId,
                        description = event.description,
                        startTimeMinutes = current.startTimeMinutes,
                        durationMinutes = storedEventDuration(
                            startTimeMinutes = current.startTimeMinutes,
                            durationMinutes = current.durationMinutes,
                        ),
                        durationDays = current.durationDays,
                    )
                }
                return@withWriteTransaction moved
            }
            if (!record.event.recurring && event.recurrence == null && occurrence == null)
                return@withWriteTransaction dao.updateDetails(
                    id = event.id,
                    name = name,
                    dueDate = event.dueDate?.toEpochDays(),
                    groupId = event.groupId,
                    description = event.description,
                    startTimeMinutes = event.startTime?.minuteOfDay(),
                    durationMinutes = event.durationMinutes,
                    durationDays = event.durationDays,
                ) > 0
            val today = clock.now().toLocalDateTime(timeZone = timeZone()).date.toEpochDays()
            val boundary = occurrence?.scheduledDate?.toEpochDays() ?: today
            closeFrom(record = record, boundary = boundary)
            val date = event.dueDate
            val newRule = event.recurrence
            if (newRule != null) {
                val start = requireNotNull(value = date)
                require(value = newRule.endDate?.let { date -> date >= start } != false)
                require(value = nextRecurrenceDates(rule = newRule, start = start, count = 1).isNotEmpty())
                dao.insertSchedule(
                    schedule = newRule.entity(
                        eventId = event.id,
                        start = start,
                        startTime = event.startTime,
                        durationMinutes = event.durationMinutes,
                        durationDays = event.durationDays,
                    )
                )
            }
            dao.updateDetails(
                id = event.id,
                name = name,
                dueDate = date?.toEpochDays(),
                groupId = event.groupId,
                description = event.description,
                startTimeMinutes = event.startTime?.minuteOfDay(),
                durationMinutes = event.durationMinutes,
                durationDays = event.durationDays,
            )
            dao.setRecurring(id = event.id, recurring = event.recurrence != null)
            true
        }
    }

    override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean {
        return database.value.withWriteTransaction {
            val record = dao.getRecord(id = id) ?: return@withWriteTransaction false
            if (record.event.recurring) return@withWriteTransaction false
            dao.reschedule(id = id, expectedDate = expectedDate?.toEpochDays(), dueDate = date?.toEpochDays()) > 0
        }
    }

    override suspend fun moveOccurrence(
        id: Int,
        occurrence: ItemOccurrenceId?,
        expectedDate: LocalDate?,
        date: LocalDate,
        scope: ItemChangeScope,
    ): Boolean {
        return database.value.withWriteTransaction {
            val record = dao.getRecord(id = id) ?: return@withWriteTransaction false
            if (occurrence == null)
                return@withWriteTransaction !record.event.recurring &&
                    dao.reschedule(
                        id = id,
                        expectedDate = expectedDate?.toEpochDays(),
                        dueDate = date.toEpochDays(),
                    ) > 0
            if (scope == ItemChangeScope.ThisOccurrence)
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
        record: EventRecord,
        occurrence: ItemOccurrenceId,
        expectedDate: LocalDate?,
        date: LocalDate,
        timing: Event? = null,
    ): Boolean {
        val id = record.event.id
        val schedule = record.schedules.find { schedule -> schedule.id == occurrence.scheduleId } ?: return false
        val event = record.occurrence(schedule = schedule, date = occurrence.scheduledDate) ?: return false
        if (event.dueDate != expectedDate) return false
        val boundary = occurrence.scheduledDate.toEpochDays()
        val rule = schedule.rule()
        val next = nextRecurrenceDates(
                rule = rule,
                start = LocalDate.fromEpochDays(epochDays = schedule.startDate),
                from = LocalDate.fromEpochDays(epochDays = boundary),
                count = 1,
            )
            .firstOrNull() ?: return false
        val newDate = if (timing != null && date == expectedDate) maxOf(a = date, b = next) else date
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
        val time = if (timing == null) event.startTime else timing.startTime
        val duration = if (timing == null) event.durationMinutes else timing.durationMinutes
        val days = timing?.durationDays ?: event.durationDays
        dao.insertSchedule(
            schedule = shifted.entity(
                eventId = id,
                start = newDate,
                startTime = time,
                durationMinutes = duration,
                durationDays = days,
            )
        )
        dao.updateDetails(
            id = id,
            name = record.event.name,
            dueDate = newDate.toEpochDays(),
            groupId = record.event.groupId,
            description = record.event.description,
            startTimeMinutes = time?.minuteOfDay(),
            durationMinutes = duration,
            durationDays = days,
        )
        dao.setRecurring(id = id, recurring = true)
        return true
    }

    override suspend fun deleteOccurrence(
        id: Int,
        occurrence: ItemOccurrenceId?,
        scope: ItemChangeScope,
    ): Boolean {
        return database.value.withWriteTransaction {
            val record = dao.getRecord(id = id) ?: return@withWriteTransaction false
            if (occurrence == null || scope == ItemChangeScope.EntireItem) {
                dao.deleteById(id = id)
                return@withWriteTransaction true
            }
            val schedule = record.schedules.find { schedule ->
                schedule.id == occurrence.scheduleId
            } ?: return@withWriteTransaction false
            if (record.occurrence(schedule = schedule, date = occurrence.scheduledDate) == null)
                return@withWriteTransaction false
            if (scope == ItemChangeScope.ThisOccurrence) {
                dao.saveOccurrence(occurrence = record.exception(id = occurrence).copy(skipped = true))
            } else {
                closeFrom(record = record, boundary = occurrence.scheduledDate.toEpochDays())
            }
            true
        }
    }

    override suspend fun delete(id: Int) = dao.deleteById(id = id)

    private fun EventRecord.exception(id: ItemOccurrenceId): EventOccurrenceEntity {
        return occurrences.find { occurrence ->
            occurrence.scheduleId == id.scheduleId && occurrence.scheduledDate == id.scheduledDate.toEpochDays()
        }
            ?: EventOccurrenceEntity(
                scheduleId = id.scheduleId,
                scheduledDate = id.scheduledDate.toEpochDays(),
                eventId = event.id,
            )
    }

    private suspend fun moveOne(
        record: EventRecord,
        occurrence: ItemOccurrenceId,
        expectedDate: LocalDate?,
        date: LocalDate,
        timing: Event? = null,
    ): Boolean {
        val schedule = record.schedules.find { schedule -> schedule.id == occurrence.scheduleId } ?: return false
        val event = record.occurrence(schedule = schedule, date = occurrence.scheduledDate) ?: return false
        if (event.dueDate != expectedDate) return false
        val previous = record.exception(id = occurrence)
        dao.saveOccurrence(
            occurrence = previous.copy(
                movedDate = date.toEpochDays().takeUnless { epochDay ->
                    epochDay == occurrence.scheduledDate.toEpochDays()
                },
                overridesTiming = timing != null || previous.overridesTiming,
                startTimeMinutes = if (timing == null) previous.startTimeMinutes else timing.startTime?.minuteOfDay(),
                durationMinutes = if (timing == null) {
                    storedEventDuration(
                        startTimeMinutes = previous.startTimeMinutes,
                        durationMinutes = previous.durationMinutes,
                    )
                } else {
                    timing.durationMinutes
                },
                durationDays = timing?.durationDays ?: previous.durationDays,
            )
        )
        return true
    }

    private suspend fun closeFrom(record: EventRecord, boundary: Long) {
        for (schedule in record.schedules) if (schedule.closedBefore?.let { closedBefore ->
            closedBefore > boundary
        } != false) {
            dao.closeSchedule(id = schedule.id, before = boundary)
            dao.deleteOccurrencesFrom(scheduleId = schedule.id, fromDate = boundary)
        }
    }
}
