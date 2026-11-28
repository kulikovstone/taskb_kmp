package com.mkulikov.taskb.feature.item.repository

import com.mkulikov.taskb.feature.item.api.database.EventOccurrenceEntity
import com.mkulikov.taskb.feature.item.api.database.EventRecord
import com.mkulikov.taskb.feature.item.api.database.EventScheduleEntity
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.item.api.model.overlapsDates
import com.mkulikov.taskb.feature.item.api.recurrence.recurrenceDates
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

internal fun EventScheduleEntity.rule(): ItemRecurrence {
    return ItemRecurrence(
        frequency = RecurrenceFrequency.valueOf(value = frequency),
        interval = interval,
        weekdays = DayOfWeek.entries.filter { weekday -> weekdays and (1 shl weekday.ordinal) != 0 }.toSet(),
        dayOfMonth = dayOfMonth,
        month = month,
        endDate = endDate?.let(block = LocalDate::fromEpochDays),
        occurrenceCount = occurrenceCount,
    )
}

internal fun LocalTime.minuteOfDay() = hour * 60 + minute

internal fun Int.localTime() = LocalTime(hour = this / 60, minute = this % 60)

internal fun storedEventDuration(startTimeMinutes: Int?, durationMinutes: Int?): Int? {
    // A missing duration in a stored timed event represents one hour in older versions of TaskB.
    return if (startTimeMinutes == null) null else durationMinutes ?: 60
}

internal fun ItemRecurrence.entity(
    eventId: Int,
    start: LocalDate,
    startTime: LocalTime? = null,
    durationMinutes: Int? = null,
    durationDays: Int = 1,
): EventScheduleEntity {
    return EventScheduleEntity(
        eventId = eventId,
        startDate = start.toEpochDays(),
        frequency = frequency.name,
        interval = interval,
        weekdays = weekdays.fold(initial = 0) { mask, day -> mask or (1 shl day.ordinal) },
        dayOfMonth = dayOfMonth,
        month = month,
        endDate = endDate?.toEpochDays(),
        occurrenceCount = occurrenceCount,
        startTimeMinutes = startTime?.minuteOfDay(),
        durationMinutes = durationMinutes,
        durationDays = durationDays,
    )
}

internal val EventRecord.currentSchedule
    get() = schedules.filter { schedule -> schedule.closedBefore == null }.maxByOrNull { schedule -> schedule.id }
private val EventRecord.displaySchedule
    get() = currentSchedule ?: schedules.maxByOrNull(selector = EventScheduleEntity::id)?.takeIf { event.recurring }

internal fun EventRecord.event(): Event {
    val time = if (event.recurring) displaySchedule?.startTimeMinutes else event.startTimeMinutes
    val duration = if (event.recurring) displaySchedule?.durationMinutes else event.durationMinutes
    return Event(
        id = event.id,
        name = event.name,
        dueDate = event.dueDate?.let(block = LocalDate::fromEpochDays),
        groupId = event.groupId,
        createdAt = Instant.fromEpochMilliseconds(epochMilliseconds = event.createdAt),
        recurrence = displaySchedule?.rule(),
        description = event.description,
        startTime = time?.localTime(),
        durationMinutes = storedEventDuration(startTimeMinutes = time, durationMinutes = duration),
        durationDays = if (event.recurring) displaySchedule?.durationDays ?: 1 else event.durationDays,
    )
}

private fun EventRecord.exceptionIndex(): Map<Pair<Long, Long>, EventOccurrenceEntity> {
    return occurrences.associateBy { occurrence -> occurrence.scheduleId to occurrence.scheduledDate }
}

internal fun EventRecord.occurrence(id: ItemOccurrenceId): Event? {
    val schedule = schedules.find { schedule -> schedule.id == id.scheduleId } ?: return null
    return occurrence(schedule = schedule, date = id.scheduledDate)
}

internal fun EventRecord.occurrence(
    schedule: EventScheduleEntity,
    date: LocalDate,
    exceptions: Map<Pair<Long, Long>, EventOccurrenceEntity> = exceptionIndex(),
): Event? {
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
    val time = if (exception?.overridesTiming == true) exception.startTimeMinutes else schedule.startTimeMinutes
    val duration = if (exception?.overridesTiming == true) exception.durationMinutes else schedule.durationMinutes
    return event()
        .copy(
            dueDate = exception?.movedDate?.let(block = LocalDate::fromEpochDays) ?: date,
            recurrence = schedule.rule(),
            occurrenceId = ItemOccurrenceId(scheduleId = schedule.id, scheduledDate = date),
            startTime = time?.localTime(),
            durationMinutes = storedEventDuration(startTimeMinutes = time, durationMinutes = duration),
            durationDays = if (exception?.overridesTiming == true) exception.durationDays else schedule.durationDays,
        )
}

internal fun EventRecord.calendar(from: LocalDate, through: LocalDate): List<Event> {
    return buildList {
        val exceptions = exceptionIndex()
        val longestDuration = (schedules.mapNotNull { schedule ->
                storedEventDuration(
                    startTimeMinutes = schedule.startTimeMinutes,
                    durationMinutes = schedule.durationMinutes,
                )
            } +
                occurrences.mapNotNull { occurrence ->
                    storedEventDuration(
                        startTimeMinutes = occurrence.startTimeMinutes,
                        durationMinutes = occurrence.durationMinutes,
                    )
                } +
                listOfNotNull(
                    element = storedEventDuration(
                        startTimeMinutes = event.startTimeMinutes,
                        durationMinutes = event.durationMinutes,
                    )
                ))
            .maxOrNull() ?: 0
        // Include starts before the window when their duration extends into the visible dates.
        val longestDays =
            (schedules.map { schedule ->
                schedule.durationDays
            } + occurrences.map(transform = EventOccurrenceEntity::durationDays) + event.durationDays).maxOrNull()
                ?: 1
        val lookback = maxOf(a = (longestDuration.toLong() + 1439) / 1440, b = longestDays.toLong() - 1)
        val searchFrom = LocalDate.fromEpochDays(epochDays = from.toEpochDays() - lookback)
        if (!event.recurring)
            event()
                .takeIf { event -> event.dueDate == null || event.overlapsDates(from = from, through = through) }
                ?.let(block = ::add)
        for (schedule in schedules) {
            val end = minOf(
                a = through.toEpochDays(),
                b = schedule.closedBefore?.minus(other = 1) ?: through.toEpochDays(),
            )
            if (end >= searchFrom.toEpochDays()) {
                for (date in
                    recurrenceDates(
                        rule = schedule.rule(),
                        start = LocalDate.fromEpochDays(epochDays = schedule.startDate),
                        from = searchFrom,
                        through = LocalDate.fromEpochDays(epochDays = end),
                    )) {
                    occurrence(schedule = schedule, date = date, exceptions = exceptions)
                        ?.takeIf { event -> event.overlapsDates(from = from, through = through) }
                        ?.let(block = ::add)
                }
            }
            for (moved in
                occurrences.filter { occurrence ->
                    occurrence.scheduleId == schedule.id &&
                        occurrence.movedDate?.let { day ->
                            day in searchFrom.toEpochDays()..through.toEpochDays()
                        } == true
                }) {
                occurrence(
                        schedule = schedule,
                        date = LocalDate.fromEpochDays(epochDays = moved.scheduledDate),
                        exceptions = exceptions,
                    )
                    ?.takeIf { event -> event.overlapsDates(from = from, through = through) }
                    ?.let(block = ::add)
            }
        }
    }
        .distinctBy(selector = Event::key)
}
