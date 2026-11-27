package com.mkulikov.taskb.feature.item.api.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "event_occurrences",
    primaryKeys = ["scheduleId", "scheduledDate"],
    foreignKeys =
        [
            ForeignKey(
                entity = EventScheduleEntity::class,
                parentColumns = ["id"],
                childColumns = ["scheduleId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = EventEntity::class,
                parentColumns = ["id"],
                childColumns = ["eventId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("eventId"), Index("scheduleId")],
)
data class EventOccurrenceEntity(
    val scheduleId: Long,
    val scheduledDate: Long,
    val eventId: Int,
    val movedDate: Long? = null,
    val skipped: Boolean = false,
    /** Distinguishes an explicit removal of time from inheriting the schedule's time. */
    @ColumnInfo(defaultValue = "0") val overridesTiming: Boolean = false,
    val startTimeMinutes: Int? = null,
    val durationMinutes: Int? = null,
    @ColumnInfo(defaultValue = "1") val durationDays: Int = 1,
)
