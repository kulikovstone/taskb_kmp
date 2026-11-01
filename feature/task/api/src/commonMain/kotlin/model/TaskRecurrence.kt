package com.mkulikov.taskb.feature.task.api.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Calendar rule; completion and date exceptions belong to individual occurrences. */
data class TaskRecurrence(
    val frequency: RecurrenceFrequency = RecurrenceFrequency.Monthly,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int = 1,
    val month: Int = 1,
    val endDate: LocalDate? = null,
    val occurrenceCount: Int? = null,
) {
    init {
        require(interval in 1..9999)
        require(dayOfMonth in 1..31 && month in 1..12)
        require(frequency != RecurrenceFrequency.Weekly || weekdays.isNotEmpty())
        require(occurrenceCount == null || occurrenceCount in 1..1_000_000)
        require(endDate == null || occurrenceCount == null)
    }

    companion object {
        fun monthly(date: LocalDate) = TaskRecurrence(dayOfMonth = date.day, month = date.month.ordinal + 1)
    }
}
