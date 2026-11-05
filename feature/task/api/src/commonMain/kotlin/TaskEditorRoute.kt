package com.mkulikov.taskb.feature.task.api

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId

/** Opens a task editor independently of the tasks list. Defaults apply only to a new task. */
sealed interface TaskEditorRoute : NavKey {
    data class Create(
        val initialDate: LocalDate? = null,
        val initialGroupId: Int? = null,
        /** Reuse the last explicitly selected group for the current app session. */
        val rememberGroupSelection: Boolean = false,
    ) : TaskEditorRoute

    data class Edit(val taskId: Int, val occurrenceId: TaskOccurrenceId? = null) : TaskEditorRoute
}
