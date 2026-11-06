package com.mkulikov.taskb.feature.holiday.api.database

import androidx.room3.Entity

/** Success marker, also persisted when the service returns no holidays for a month. */
@Entity(tableName = "holiday_months", primaryKeys = ["locationKey", "year", "month"])
data class HolidayMonthEntity(
    val locationKey: String,
    val year: Int,
    val month: Int,
    val fetchedAt: Long,
)
