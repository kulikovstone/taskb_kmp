package com.mkulikov.taskb.feature.item.api.repository

import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ITaskRepository : IItemRepository<Task> {
    val tasks: Flow<List<Task>>

    suspend fun save(task: Task): Boolean

    suspend fun setCompleted(id: Int, completed: Boolean)

    override fun observeCalendar(from: LocalDate, through: LocalDate): Flow<List<Task>> = tasks

    fun observeTaskList(today: LocalDate): Flow<List<Task>> = tasks

    suspend fun saveOccurrence(task: Task, scope: ItemChangeScope): Boolean {
        return if (task.occurrenceId == null) save(task = task) else false
    }

    suspend fun setOccurrenceCompleted(id: Int, occurrence: ItemOccurrenceId?, completed: Boolean): Boolean {
        if (occurrence != null) return false
        setCompleted(id = id, completed = completed)
        return true
    }

    suspend fun quickDeleteOccurrence(
        id: Int,
        occurrence: ItemOccurrenceId,
        expectedDate: LocalDate,
    ): Boolean = false
}
