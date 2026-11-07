package com.mkulikov.taskb.feature.task.api.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import kotlin.time.Clock

@Entity(
    tableName = "tasks",
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
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dueDate: Long? = null,
    val groupId: Int? = null,
    /** Creation time in milliseconds since the Unix epoch. */
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    /** Completion time in milliseconds since the Unix epoch, or null for an incomplete task. */
    val completedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val recurring: Boolean = false,
    @ColumnInfo(defaultValue = "''") val description: String = "",
)
