package com.mkulikov.taskb.feature.item.api.database

import androidx.room3.Embedded
import androidx.room3.Relation

data class TaskRecord(
    @Embedded val task: TaskEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["taskId"]) val schedules: List<TaskScheduleEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["taskId"]) val occurrences: List<TaskOccurrenceEntity>,
)
