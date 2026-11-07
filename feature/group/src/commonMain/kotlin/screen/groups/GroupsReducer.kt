package com.mkulikov.taskb.feature.group.screen.groups

import com.mkulikov.taskb.feature.group.api.model.Group

internal sealed interface GroupsMessage {
    data class Loaded(val groups: List<Group>) : GroupsMessage

    data object Loading : GroupsMessage

    data object LoadFailed : GroupsMessage

    data class FilterChanged(val filter: GroupFilter) : GroupsMessage

    data class SortChanged(val sort: GroupSort) : GroupsMessage
}

internal fun reduceGroups(state: GroupsState, message: GroupsMessage): GroupsState {
    return when (message) {
        is GroupsMessage.Loaded -> state.copy(groups = message.groups, isLoading = false, loadError = null)
        GroupsMessage.Loading -> state.copy(isLoading = true, loadError = null)
        GroupsMessage.LoadFailed -> state.copy(isLoading = false, loadError = "Couldn't load groups. Please try again.")
        is GroupsMessage.FilterChanged -> state.copy(filter = message.filter)
        is GroupsMessage.SortChanged -> state.copy(
            sort = message.sort,
            reverseSort = state.sort == message.sort && !state.reverseSort,
        )
    }
}
