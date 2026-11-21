package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskFilter
import kotlin.time.Duration.Companion.hours

// Persisted group IDs are positive. Null means no group filter.
internal const val noGroupFilterId = 0

internal fun CalendarState.directoryTasks(): List<Task> {
    val cutoff = now - 24.hours
    return taskList.orEmpty().filter { task ->
        task.completedAt?.let { completedAt -> completedAt > cutoff } ?: true
    }
}

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
