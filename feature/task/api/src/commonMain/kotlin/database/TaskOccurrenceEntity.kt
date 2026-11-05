package com.mkulikov.taskb.feature.task.api.database

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(tableName = "task_occurrences", primaryKeys = ["scheduleId", "scheduledDate"], foreignKeys = [
    ForeignKey(entity = TaskScheduleEntity::class, parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("taskId"), Index("scheduleId")])
data class TaskOccurrenceEntity(
    val scheduleId: Long,
    val scheduledDate: Long,
    val taskId: Int,
    val movedDate: Long? = null,
    val completedAt: Long? = null,
    val skipped: Boolean = false,
)
