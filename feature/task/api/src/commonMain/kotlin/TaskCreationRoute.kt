package com.mkulikov.taskb.feature.task.api

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate

data class TaskCreationRoute(
    val initialDate: LocalDate? = null,
    val initialGroupId: Int? = null,
) : NavKey
