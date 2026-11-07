package com.mkulikov.taskb.feature.holiday.api.database

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "holidays",
    primaryKeys = ["locationKey", "year", "month", "id"],
    foreignKeys =
        [
            ForeignKey(
                entity = HolidayMonthEntity::class,
                parentColumns = ["locationKey", "year", "month"],
                childColumns = ["locationKey", "year", "month"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class HolidayEntity(
    val locationKey: String,
    val year: Int,
    val month: Int,
    val id: String,
    val date: Long,
    val name: String,
    val countryCode: String,
)
