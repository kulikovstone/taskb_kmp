package com.mkulikov.taskb.feature.task.model

import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

internal data class Task(
    val id: Int = 0,
    val name: String,
    val dueDate: LocalDate? = null,
    val groupId: Int? = null,
    val createdAt: Instant = Clock.System.now(),
    val completedAt: Instant? = null,
) {
    val isCompleted: Boolean get() = completedAt != null
}
