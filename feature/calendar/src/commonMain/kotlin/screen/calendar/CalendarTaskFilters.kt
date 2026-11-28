package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.api.model.TaskFilter

// Persisted group IDs are positive. Null means no group filter.
internal const val noGroupFilterId = 0

internal fun CalendarState.directoryTasks(): List<Task> {
    val cutoff = now - Task.listVisibilityAfterCompletion
    return taskList.orEmpty().filter { task ->
        task.completedAt?.let { completedAt -> completedAt > cutoff } ?: true
    }
}

internal fun List<Task>.countTasksByGroup(): Map<Int?, Int> = asSequence()
    .distinctBy(selector = Task::id)
    .groupingBy(keySelector = Task::groupId)
    .eachCount()

internal fun CalendarState.filteredTasks(query: String, filter: TaskFilter?, groupId: Int?): List<Task> {
    val source = if (selectedDate == null) directoryTasks() else sidebarTasks
    val search = query.trim()
    return source.filter { task ->
        task.name.contains(other = search, ignoreCase = true) &&
            (groupId == null || (task.groupId ?: noGroupFilterId) == groupId) &&
            (filter?.matches(task = task, today = today) ?: true)
    }.sortedWith(
        comparator = compareBy<Task>(selector = Task::isCompleted)
            .thenBy { task -> task.dueDate == null }
            .thenBy(selector = Task::dueDate)
            .thenByDescending(selector = Task::createdAt)
            .thenByDescending(selector = Task::id),
    )
}

internal fun CalendarState.filteredGroupEvents(
    query: String,
    filter: TaskFilter?,
    groupId: Int?,
): List<CalendarItem.EventItem> {
    if (selectedDate == null || groupId == null || filter != null) return emptyList()
    val search = query.trim()
    return sidebarEventItems.filterIsInstance<CalendarItem.EventItem>().filter { item ->
        (item.groupId ?: noGroupFilterId) == groupId && item.name.contains(other = search, ignoreCase = true)
    }
}
