package com.mkulikov.taskb.feature.item.api.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

data class Event(
    override val id: Int = 0,
    override val name: String,
    /** Nullable for legacy records; the editor always supplies a date. */
    override val dueDate: LocalDate? = null,
    override val groupId: Int? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val recurrence: ItemRecurrence? = null,
    override val occurrenceId: ItemOccurrenceId? = null,
    override val description: String = "",
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = if (startTime != null) DEFAULT_DURATION_MINUTES else null,
    /** Inclusive number of dates occupied by an event without time. */
    val durationDays: Int = 1,
) : Item {
    override val type: ItemType
        get() = ItemType.Event

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

    companion object {
        const val DEFAULT_DURATION_MINUTES = 60
    }
}
