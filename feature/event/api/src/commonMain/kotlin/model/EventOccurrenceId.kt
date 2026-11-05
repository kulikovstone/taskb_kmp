package com.mkulikov.taskb.feature.event.api.model

import kotlinx.datetime.LocalDate

/** Stable identity uses the scheduled date, even when the event is moved elsewhere. */
data class EventOccurrenceId(val scheduleId: Long, val scheduledDate: LocalDate)
