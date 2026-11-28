package com.mkulikov.taskb.feature.item.api.model

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class Task(
    override val id: Int = 0,
    override val name: String,
    override val dueDate: LocalDate? = null,
    override val groupId: Int? = null,
    override val createdAt: Instant = Clock.System.now(),
    val completedAt: Instant? = null,
    override val recurrence: ItemRecurrence? = null,
    override val occurrenceId: ItemOccurrenceId? = null,
    override val description: String = "",
    val showInCalendar: Boolean = true,
    val quickDelete: Boolean = false,
) : Item {
    override val type: ItemType
        get() = ItemType.Task

    val isCompleted: Boolean
        get() = completedAt != null

    val canQuickDelete: Boolean
        get() = quickDelete && !isCompleted && recurrence != null && occurrenceId != null

    companion object {
        val listVisibilityAfterCompletion = 24.hours
    }
}
