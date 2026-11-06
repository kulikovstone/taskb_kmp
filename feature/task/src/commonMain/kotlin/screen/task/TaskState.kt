package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.core.utils.time.currentDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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
    private val unexpiredTasks: List<Task> get() = tasks.filter {
        val completedAt = it.completedAt
        (completedAt == null || completedAt > now - 24.hours) &&
            (it.occurrenceId == null || !it.isCompleted || filter == TaskFilter.Completed)
    }
    val taskCount: Int get() = unexpiredTasks.size
    val nextCompletionExpiry: Instant? get() = tasks.asSequence()
        .mapNotNull { it.completedAt?.plus(24.hours) }
        .filter { it > now }
        .minOrNull()

    val visibleTasks: List<Task> get() {
        val matching = unexpiredTasks.filter { task ->
            task.name.contains(query.text.toString().trim(), ignoreCase = true) && when (val group = groupFilter) {
                TaskGroupFilter.All -> true
                TaskGroupFilter.NoGroup -> task.groupId == null
                is TaskGroupFilter.Selected -> task.groupId == group.id
            } && when (filter) {
                null -> true
                TaskFilter.Completed -> task.isCompleted
                else -> !task.isCompleted && when (filter) {
                    TaskFilter.Scheduled -> task.dueDate != null
                    TaskFilter.Overdue -> task.dueDate?.let { it < today } == true
                    TaskFilter.Today -> task.dueDate == today
                    TaskFilter.Tomorrow -> task.dueDate == today.plus(1, DateTimeUnit.DAY)
                    TaskFilter.OtherDates -> task.dueDate?.let { it > today.plus(1, DateTimeUnit.DAY) } == true
                    TaskFilter.NoDate -> task.dueDate == null
                }
            }
        }
        val order = when (sort) {
            TaskSort.Name -> compareBy<Task> { it.name.lowercase() }
            TaskSort.Group -> {
                val groupNames = groups.associate { it.id to it.name.lowercase() }
                compareBy<Task> { task -> task.groupId?.let(groupNames::get) ?: "" }
            }
            TaskSort.Date -> compareBy<Task> { it.dueDate == null }.thenBy { it.dueDate }
        }.let { if (reverseSort) it.reversed() else it }
        return matching.sortedWith(compareBy<Task> { it.isCompleted }.then(order)
            .thenByDescending { it.createdAt }.thenByDescending { it.id })
    }
    val summary: String get() {
        val total = taskCount
        return if (query.text.isBlank() && filter == null && groupFilter == TaskGroupFilter.All) {
            "$total ${if (total == 1) "task" else "tasks"}"
        } else "${visibleTasks.size} of $total tasks"
    }
}

internal enum class TaskFilter(val label: String) {
    Scheduled("Scheduled"), Overdue("Overdue"), Today("Today"), Tomorrow("Tomorrow"),
    OtherDates("Other dates"), NoDate("No date"), Completed("Completed"),
}
internal enum class TaskSort { Name, Group, Date }
internal sealed interface TaskGroupFilter {
    data object All : TaskGroupFilter
    data object NoGroup : TaskGroupFilter
    data class Selected(val id: Int) : TaskGroupFilter
}
internal enum class TaskSelection { Filters }
