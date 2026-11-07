package com.mkulikov.taskb.feature.event.api.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import kotlin.time.Clock

@Entity(
    tableName = "events",
    foreignKeys =
        [
            ForeignKey(
                entity = GroupEntity::class,
                parentColumns = ["id"],
                childColumns = ["groupId"],
                onDelete = ForeignKey.SET_NULL,
            )
        ],
    indices = [Index("groupId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dueDate: Long? = null,
    val groupId: Int? = null,
    /** Creation time in milliseconds since the Unix epoch. */
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    @ColumnInfo(defaultValue = "0") val recurring: Boolean = false,
    @ColumnInfo(defaultValue = "''") val description: String = "",
    /** Local start time as minutes since midnight. */
    val startTimeMinutes: Int? = null,
    val durationMinutes: Int? = null,
    @ColumnInfo(defaultValue = "1") val durationDays: Int = 1,
)
