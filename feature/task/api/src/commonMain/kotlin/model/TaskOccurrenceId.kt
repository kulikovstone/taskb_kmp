package com.mkulikov.taskb.feature.task.api.model

import kotlinx.datetime.LocalDate

/** Stable identity uses the scheduled date, even when the event is moved elsewhere. */
data class TaskOccurrenceId(val scheduleId: Long, val scheduledDate: LocalDate)
