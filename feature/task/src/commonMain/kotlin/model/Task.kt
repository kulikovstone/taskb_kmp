package com.mkulikov.taskb.feature.task.model

import kotlinx.datetime.LocalDate

internal data class Task(
    val id: Int = 0,
    val name: String,
    val dueDate: LocalDate? = null,
    val groupId: Int? = null,
    val completed: Boolean = false,
)
