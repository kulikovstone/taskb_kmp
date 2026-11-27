package com.mkulikov.taskb.feature.item.api.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

sealed interface Item {
    val type: ItemType
    val id: Int
    val name: String
    val description: String
    val dueDate: LocalDate?
    val groupId: Int?
    val createdAt: Instant
    val recurrence: ItemRecurrence?
    val occurrenceId: ItemOccurrenceId?

    val key: String
        get() {
            val base = "${type.keyPrefix}$id"
            return occurrenceId?.let { occurrence ->
                "$base:${occurrence.scheduleId}:${occurrence.scheduledDate}"
            } ?: base
        }
}
