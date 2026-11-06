package com.mkulikov.taskb.feature.group.screen.group_selection

import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.model.Group

internal sealed interface GroupSelectionMessage {
    data class Loaded(val groups: List<Group>) : GroupSelectionMessage
    data class Selected(val result: GroupSelectionResult) : GroupSelectionMessage
    data object Loading : GroupSelectionMessage
    data object LoadFailed : GroupSelectionMessage
}

internal fun reduceGroupSelection(state: GroupSelectionState, message: GroupSelectionMessage): GroupSelectionState = when (message) {
    is GroupSelectionMessage.Loaded -> state.copy(groups = message.groups, isLoading = false, loadError = null)
    is GroupSelectionMessage.Selected -> state.copy(result = message.result)
    GroupSelectionMessage.Loading -> state.copy(isLoading = true, loadError = null)
    GroupSelectionMessage.LoadFailed -> state.copy(isLoading = false, loadError = "Couldn't load groups. Please try again.")
}
