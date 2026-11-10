package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.model.Group

internal data class GroupsState(
    val groups: List<Group> = emptyList(),
    val query: TextFieldState = TextFieldState(),
    val filter: GroupFilter = GroupFilter.All,
    val sort: GroupSort = GroupSort.Name,
    val reverseSort: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: String? = null,
) {
    val withTasksCount: Int
        get() = groups.count { group -> group.tasks.planned > 0 }

    val emptyCount: Int
        get() = groups.size - withTasksCount

    val visibleGroups: List<Group>
        get() {
            val matching = groups.filter { group ->
                group.name.contains(other = query.text.toString().trim(), ignoreCase = true) &&
                    when (filter) {
                        GroupFilter.All -> true
                        GroupFilter.WithTasks -> group.tasks.planned > 0
                        GroupFilter.Empty -> group.tasks.planned == 0
                    }
            }
            val byName = compareBy<Group> { group -> group.name.lowercase() }.thenBy(selector = Group::id)
            val comparator = when (sort) {
                GroupSort.Name -> byName
                GroupSort.Tasks -> compareByDescending<Group> { group -> group.tasks.planned }.then(comparator = byName)
                GroupSort.Events -> compareByDescending(selector = Group::eventCount).then(comparator = byName)
            }
            return matching.sortedWith(comparator = if (reverseSort) comparator.reversed() else comparator)
        }

    val summary: String
        get() = if (query.text.isBlank() && filter == GroupFilter.All) {
            "${groups.size} ${if (groups.size == 1) "group" else "groups"}"
        } else {
            "${visibleGroups.size} of ${groups.size} groups"
        }
}

internal enum class GroupFilter {
    All,
    WithTasks,
    Empty,
}

internal enum class GroupSort {
    Name,
    Tasks,
    Events,
}
