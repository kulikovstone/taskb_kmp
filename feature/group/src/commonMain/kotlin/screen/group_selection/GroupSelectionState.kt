package com.mkulikov.taskb.feature.group.screen.group_selection

import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.model.Group

internal data class GroupSelectionState(
    val selectedGroupId: Int? = null,
    val showNoGroup: Boolean = false,
    val noGroupSelected: Boolean = false,
    val groups: List<Group> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val result: GroupSelectionResult? = null,
) {
    val matchingGroups: List<Group>
        get() = groups.filter { it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy<Group> { it.name.lowercase() }.thenBy { it.id })
    val isNoGroupVisible: Boolean
        get() = showNoGroup && "No group".contains(query.trim(), ignoreCase = true)
}
