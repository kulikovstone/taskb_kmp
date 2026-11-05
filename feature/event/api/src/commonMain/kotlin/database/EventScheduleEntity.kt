package com.mkulikov.taskb.feature.event.api.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "event_schedules", foreignKeys = [ForeignKey(
    entity = EventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("eventId")])
data class EventScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Int,
    val startDate: Long,
    val frequency: String,
    val interval: Int,
    val weekdays: Int,
    val dayOfMonth: Int,
    val month: Int,
    val endDate: Long? = null,
    val occurrenceCount: Int? = null,
    /** Exclusive boundary of this revision. Null denotes the current schedule. */
    val closedBefore: Long? = null,
    val startTimeMinutes: Int? = null,
    val durationMinutes: Int? = null,
    @ColumnInfo(defaultValue = "1") val durationDays: Int = 1,
)
