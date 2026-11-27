package com.mkulikov.taskb.feature.item.api

import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId

data class TaskEditorRoute(val taskId: Int, val occurrenceId: ItemOccurrenceId? = null) : NavKey
