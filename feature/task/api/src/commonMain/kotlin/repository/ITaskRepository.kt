package com.mkulikov.taskb.feature.task.api.repository

import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ITaskRepository {
    val tasks: Flow<List<Task>>
    suspend fun getById(id: Int): Task?
    suspend fun save(task: Task): Boolean
    suspend fun setCompleted(id: Int, completed: Boolean)
    /** Changes only the date, if it still matches [expectedDate]. Returns false for stale or deleted tasks. */
    suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean
    suspend fun delete(id: Int)
}
