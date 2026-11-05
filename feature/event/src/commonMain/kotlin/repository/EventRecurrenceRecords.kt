package com.mkulikov.taskb.feature.event.repository

import com.mkulikov.taskb.feature.event.api.database.EventOccurrenceEntity
import com.mkulikov.taskb.feature.event.api.database.EventRecord
import com.mkulikov.taskb.feature.event.api.database.EventScheduleEntity
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.feature.event.api.model.overlapsDates
import com.mkulikov.taskb.feature.task.api.recurrence.recurrenceDates
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

internal fun EventScheduleEntity.rule() = EventRecurrence(
    frequency = RecurrenceFrequency.valueOf(frequency), interval = interval,
    weekdays = DayOfWeek.entries.filter { weekdays and (1 shl it.ordinal) != 0 }.toSet(),
    dayOfMonth = dayOfMonth, month = month, endDate = endDate?.let(LocalDate::fromEpochDays), occurrenceCount = occurrenceCount,
)

internal fun LocalTime.minuteOfDay() = hour * 60 + minute
internal fun Int.localTime() = LocalTime(this / 60, this % 60)
internal fun normalizedEventDuration(startTimeMinutes: Int?, durationMinutes: Int?): Int? =
    if (startTimeMinutes == null) null else durationMinutes ?: 60

internal fun EventRecurrence.entity(eventId: Int, start: LocalDate, startTime: LocalTime? = null, durationMinutes: Int? = null, durationDays: Int = 1) = EventScheduleEntity(
    eventId = eventId, startDate = start.toEpochDays(), frequency = frequency.name, interval = interval,
    weekdays = weekdays.fold(0) { mask, day -> mask or (1 shl day.ordinal) }, dayOfMonth = dayOfMonth, month = month,
    endDate = endDate?.toEpochDays(), occurrenceCount = occurrenceCount,
    startTimeMinutes = startTime?.minuteOfDay(), durationMinutes = normalizedEventDuration(startTime?.minuteOfDay(), durationMinutes),
    durationDays = durationDays,
)

internal val EventRecord.currentSchedule get() = schedules.filter { it.closedBefore == null }.maxByOrNull { it.id }
private val EventRecord.displaySchedule get() = currentSchedule ?: schedules.maxByOrNull { it.id }?.takeIf { event.recurring }

internal fun EventRecord.event(): Event {
    val time = if (event.recurring) displaySchedule?.startTimeMinutes else event.startTimeMinutes
    val duration = if (event.recurring) displaySchedule?.durationMinutes else event.durationMinutes
    return Event(
        id = event.id, name = event.name, dueDate = event.dueDate?.let(LocalDate::fromEpochDays), groupId = event.groupId,
        createdAt = Instant.fromEpochMilliseconds(event.createdAt),
        recurrence = displaySchedule?.rule(),
        description = event.description,
        startTime = time?.localTime(), durationMinutes = normalizedEventDuration(time, duration),
        durationDays = if (event.recurring) displaySchedule?.durationDays ?: 1 else event.durationDays,
    )
}

private fun EventRecord.exceptionIndex() = occurrences.associateBy { it.scheduleId to it.scheduledDate }

internal fun EventRecord.occurrence(
    schedule: EventScheduleEntity, date: LocalDate,
    exceptions: Map<Pair<Long, Long>, EventOccurrenceEntity> = exceptionIndex(),
): Event? {
    if (schedule.closedBefore?.let { date.toEpochDays() >= it } == true) return null
    if (recurrenceDates(schedule.rule(), LocalDate.fromEpochDays(schedule.startDate), date, date).isEmpty()) return null
    val exception = exceptions[schedule.id to date.toEpochDays()]
    if (exception?.skipped == true) return null
    val time = if (exception?.overridesTiming == true) exception.startTimeMinutes else schedule.startTimeMinutes
    val duration = if (exception?.overridesTiming == true) exception.durationMinutes else schedule.durationMinutes
    return event().copy(
        dueDate = exception?.movedDate?.let(LocalDate::fromEpochDays) ?: date,
        recurrence = schedule.rule(), occurrenceId = EventOccurrenceId(schedule.id, date),
        startTime = time?.localTime(), durationMinutes = normalizedEventDuration(time, duration),
        durationDays = if (exception?.overridesTiming == true) exception.durationDays else schedule.durationDays,
    )
}

internal fun EventRecord.calendar(from: LocalDate, through: LocalDate): List<Event> = buildList {
    val exceptions = exceptionIndex()
    val longestDuration = (schedules.mapNotNull { normalizedEventDuration(it.startTimeMinutes, it.durationMinutes) } +
        occurrences.mapNotNull { normalizedEventDuration(it.startTimeMinutes, it.durationMinutes) } +
        listOfNotNull(normalizedEventDuration(event.startTimeMinutes, event.durationMinutes))).maxOrNull() ?: 0
    // Include starts before the window when their duration extends into the visible dates.
    val longestDays = (schedules.map { it.durationDays } + occurrences.map { it.durationDays } + event.durationDays).maxOrNull() ?: 1
    val lookback = maxOf((longestDuration.toLong() + 1439) / 1440, longestDays.toLong() - 1)
    val searchFrom = LocalDate.fromEpochDays(from.toEpochDays() - lookback)
    if (!event.recurring) event().takeIf { it.dueDate == null || it.overlapsDates(from, through) }?.let(::add)
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
