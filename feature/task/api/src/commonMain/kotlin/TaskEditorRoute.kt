package com.mkulikov.taskb.feature.task.api

import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId

data class TaskEditorRoute(val taskId: Int, val occurrenceId: TaskOccurrenceId? = null) : NavKey
