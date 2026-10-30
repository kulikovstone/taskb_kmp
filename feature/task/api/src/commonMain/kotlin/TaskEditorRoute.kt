package com.mkulikov.taskb.feature.task.api

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate

/** Opens a task editor independently of the tasks list. Defaults apply only to a new task. */
sealed interface TaskEditorRoute : NavKey {
    data class Create(
        val initialDate: LocalDate? = null,
        val initialGroupId: Int? = null,
    ) : TaskEditorRoute

    data class Edit(val taskId: Int) : TaskEditorRoute
}
