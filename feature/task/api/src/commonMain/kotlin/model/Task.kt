package com.mkulikov.taskb.feature.task.api.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.time.Instant

data class Task(
    val id: Int = 0,
    val name: String,
    val dueDate: LocalDate? = null,
    val groupId: Int? = null,
    val createdAt: Instant = Clock.System.now(),
    val completedAt: Instant? = null,
    val recurrence: TaskRecurrence? = null,
    val occurrenceId: TaskOccurrenceId? = null,
    val description: String = "",
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = if (startTime != null) 60 else null,
) {
    val isCompleted: Boolean get() = completedAt != null
    val isTimed: Boolean get() = dueDate != null && startTime != null
    val key: String get() = occurrenceId?.let { "$id:${it.scheduleId}:${it.scheduledDate}" } ?: id.toString()
}
