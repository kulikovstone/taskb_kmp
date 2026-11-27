package com.mkulikov.taskb.feature.item.api.model

import kotlinx.datetime.LocalDate

/** Stable identity uses the scheduled date, even when the occurrence is moved elsewhere. */
data class ItemOccurrenceId(val scheduleId: Long, val scheduledDate: LocalDate)
