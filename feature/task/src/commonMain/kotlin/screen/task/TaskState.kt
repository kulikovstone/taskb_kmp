package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskFilter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

internal data class TaskState(
    val tasks: List<Task> = emptyList(),
    val groups: List<Group> = emptyList(),
    val query: TextFieldState = TextFieldState(),
    val groupFilter: TaskGroupFilter = TaskGroupFilter.All,
    val filter: TaskFilter? = null,
    val sort: TaskSort = TaskSort.Date,
    val reverseSort: Boolean = false,
    val today: LocalDate = currentDate(),
    val now: Instant = Clock.System.now(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val selection: TaskSelection? = null,
    val pendingCompletion: Set<Int> = emptySet(),
    val error: String? = null,
) {
    private val unexpiredTasks: List<Task>
        get() = tasks.filter { task ->
            val completedAt = task.completedAt
            (completedAt == null || completedAt > now - 24.hours) &&
                (task.occurrenceId == null || !task.isCompleted || filter == TaskFilter.Completed)
        }

    val taskCount: Int
        get() = unexpiredTasks.size

    val nextCompletionExpiry: Instant?
        get() = tasks
            .asSequence()
            .mapNotNull { task -> task.completedAt?.plus(duration = 24.hours) }
            .filter { instant -> instant > now }
            .minOrNull()

    val visibleTasks: List<Task>
        get() {
            val matching = unexpiredTasks.filter { task ->
                task.name.contains(other = query.text.toString().trim(), ignoreCase = true) &&
                    when (val group = groupFilter) {
                        TaskGroupFilter.All -> true
                        TaskGroupFilter.NoGroup -> task.groupId == null
                        is TaskGroupFilter.Selected -> task.groupId == group.id
                    } &&
                    (filter?.matches(task = task, today = today) ?: true)
            }
            val order = when (sort) {
                TaskSort.Name -> compareBy<Task> { task -> task.name.lowercase() }
                TaskSort.Group -> {
                    val groupNames = groups.associate { group -> group.id to group.name.lowercase() }
                    compareBy<Task> { task -> task.groupId?.let(block = groupNames::get) ?: "" }
                }
                TaskSort.Date -> compareBy<Task> { task -> task.dueDate == null }.thenBy(selector = Task::dueDate)
            }.let { comparator -> if (reverseSort) comparator.reversed() else comparator }
            return matching.sortedWith(
                comparator = compareBy<Task>(selector = Task::isCompleted)
                    .then(comparator = order)
                    .thenByDescending(selector = Task::createdAt)
                    .thenByDescending(selector = Task::id)
            )
        }

    val summary: String
        get() {
            val total = taskCount
            return if (query.text.isBlank() && filter == null && groupFilter == TaskGroupFilter.All) {
                "$total ${if (total == 1) "task" else "tasks"}"
            } else {
                "${visibleTasks.size} of $total tasks"
            }
        }
}

internal enum class TaskSort {
    Name,
    Group,
    Date,
}

internal sealed interface TaskGroupFilter {
    data object All : TaskGroupFilter

    data object NoGroup : TaskGroupFilter

    data class Selected(val id: Int) : TaskGroupFilter
}

internal enum class TaskSelection {
    Filters
}
