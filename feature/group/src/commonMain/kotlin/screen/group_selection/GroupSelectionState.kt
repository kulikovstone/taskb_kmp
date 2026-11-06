package com.mkulikov.taskb.feature.group.screen.group_selection

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.model.Group

internal data class GroupSelectionState(
    val selectedGroupId: Int? = null,
    val showNoGroup: Boolean = false,
    val noGroupSelected: Boolean = false,
    val groups: List<Group> = emptyList(),
    val query: TextFieldState = TextFieldState(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val result: GroupSelectionResult? = null,
) {
    val matchingGroups: List<Group>
        get() = groups.filter { it.name.contains(query.text.toString().trim(), ignoreCase = true) }
            .sortedWith(compareBy<Group> { it.name.lowercase() }.thenBy { it.id })
    val isNoGroupVisible: Boolean
        get() = showNoGroup && "No group".contains(query.text.toString().trim(), ignoreCase = true)
}
