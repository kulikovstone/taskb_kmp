package com.mkulikov.taskb.feature.task.api

import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import kotlinx.datetime.LocalDate

/** Opens a task editor independently of the tasks list. Defaults apply only to a new task. */
sealed interface TaskEditorRoute : NavKey {
    data class Create(
        val initialDate: LocalDate? = null,
        val initialGroupId: Int? = null,
        /** Reuse the last explicitly selected group for the current app session. */
        val rememberGroupSelection: Boolean = false,
    ) : TaskEditorRoute

    data class Edit(val taskId: Int, val occurrenceId: TaskOccurrenceId? = null) : TaskEditorRoute

    data class EditInItemEditor(val taskId: Int, val occurrenceId: TaskOccurrenceId? = null) : TaskEditorRoute
}
