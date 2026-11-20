package com.mkulikov.taskb.feature.task.api.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class TaskFilter(val label: String) {
    Scheduled(label = "Scheduled"),
    Overdue(label = "Overdue"),
    Today(label = "Today"),
    Tomorrow(label = "Tomorrow"),
    OtherDates(label = "Other dates"),
    NoDate(label = "No date"),
    Completed(label = "Completed"),
    ;

    fun matches(task: Task, today: LocalDate): Boolean {
        if (task.isCompleted) return this == Completed
        val date = task.dueDate
        return when (this) {
            Scheduled -> date != null
            Overdue -> date != null && date < today
            Today -> date == today
            Tomorrow -> date == today.plus(value = 1, unit = DateTimeUnit.DAY)
            OtherDates -> date != null && date > today.plus(value = 1, unit = DateTimeUnit.DAY)
            NoDate -> date == null
            Completed -> false
        }
    }
}
