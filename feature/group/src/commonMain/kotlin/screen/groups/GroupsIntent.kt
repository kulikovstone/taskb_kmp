package com.mkulikov.taskb.feature.group.screen.groups

internal sealed interface GroupsIntent {
    data class Search(val query: String) : GroupsIntent
    data class Filter(val filter: GroupFilter) : GroupsIntent
    data class Sort(val sort: GroupSort) : GroupsIntent
    data object RetryLoad : GroupsIntent
}
