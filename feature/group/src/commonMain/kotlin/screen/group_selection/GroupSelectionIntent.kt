package com.mkulikov.taskb.feature.group.screen.group_selection

internal sealed interface GroupSelectionIntent {
    data class Search(val query: String) : GroupSelectionIntent
    data class Select(val groupId: Int?) : GroupSelectionIntent
    data object RetryLoad : GroupSelectionIntent
}
