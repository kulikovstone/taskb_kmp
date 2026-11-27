package com.mkulikov.taskb.feature.item.api.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Calendar rule; completion and date exceptions belong to individual occurrences. */
data class ItemRecurrence(
    val frequency: RecurrenceFrequency = RecurrenceFrequency.Monthly,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int = 1,
    val month: Int = 1,
    val endDate: LocalDate? = null,
    val occurrenceCount: Int? = null,
) {
    init {
        require(value = interval in 1..9999)
        require(value = dayOfMonth in 1..31 && month in 1..12)
        require(value = frequency != RecurrenceFrequency.Weekly || weekdays.isNotEmpty())
        require(value = occurrenceCount == null || occurrenceCount in 1..1_000_000)
        require(value = endDate == null || occurrenceCount == null)
    }

    companion object {
        fun monthly(date: LocalDate) = ItemRecurrence(dayOfMonth = date.day, month = date.month.ordinal + 1)
    }
}
