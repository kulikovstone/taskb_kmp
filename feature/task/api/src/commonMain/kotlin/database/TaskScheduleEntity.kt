package com.mkulikov.taskb.feature.task.api.database

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "task_schedules", foreignKeys = [ForeignKey(
    entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("taskId")])
data class TaskScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Int,
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
)
