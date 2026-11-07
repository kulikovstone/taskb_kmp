package com.mkulikov.taskb.feature.event.api.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

data class Event(
    val id: Int = 0,
    val name: String,
    /** Nullable for legacy records; the editor always supplies a date. */
    val dueDate: LocalDate? = null,
    val groupId: Int? = null,
    val createdAt: Instant = Clock.System.now(),
    val recurrence: EventRecurrence? = null,
    val occurrenceId: EventOccurrenceId? = null,
    val description: String = "",
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = if (startTime != null) 30 else null,
    /** Inclusive number of dates occupied by an event without time. */
    val durationDays: Int = 1,
) {
    val endDate: LocalDate?
        get() = dueDate?.let { date ->
            if (startTime == null) {
                date.plus(value = durationDays.toLong() - 1, unit = DateTimeUnit.DAY)
            } else {
                endDateTime?.date
            }
        }

    val isTimed: Boolean
        get() = dueDate != null && startTime != null

    val key: String
        get() = occurrenceId?.let { occurrenceId ->
            "$id:${occurrenceId.scheduleId}:${occurrenceId.scheduledDate}"
        } ?: id.toString()
}
