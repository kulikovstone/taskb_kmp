package com.mkulikov.taskb.feature.item.screen.task

import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.TaskFilter

internal sealed interface TaskIntent {
    data class Filter(val filter: TaskFilter?) : TaskIntent

    data class FilterGroup(val filter: TaskGroupFilter) : TaskIntent

    data class Sort(val sort: TaskSort) : TaskIntent

    data class ToggleCompleted(val id: Int, val occurrenceId: ItemOccurrenceId? = null) : TaskIntent

    data class Choose(val selection: TaskSelection) : TaskIntent

    data object CloseSelection : TaskIntent

    data object RetryLoad : TaskIntent

    data object DismissError : TaskIntent
}
