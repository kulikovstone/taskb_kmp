package com.mkulikov.taskb.feature.task.api.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

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
    val showInCalendar: Boolean = true,
) {
    val isCompleted: Boolean
        get() = completedAt != null

    val key: String
        get() = occurrenceId?.let { occurrenceId ->
            "$id:${occurrenceId.scheduleId}:${occurrenceId.scheduledDate}"
        } ?: id.toString()
}
