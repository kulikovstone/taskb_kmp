package com.mkulikov.taskb.feature.task.api.repository

import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
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
    fun observeCalendar(from: LocalDate, through: LocalDate): Flow<List<Task>> = tasks
    fun observeTaskList(today: LocalDate): Flow<List<Task>> = tasks
    suspend fun getOccurrence(id: Int, occurrence: TaskOccurrenceId): Task? = getById(id)?.takeIf { it.occurrenceId == occurrence }
    suspend fun saveOccurrence(task: Task, scope: TaskChangeScope): Boolean = if (task.occurrenceId == null) save(task) else false
    suspend fun setOccurrenceCompleted(id: Int, occurrence: TaskOccurrenceId?, completed: Boolean): Boolean {
        if (occurrence != null) return false
        setCompleted(id, completed)
        return true
    }
    suspend fun moveOccurrence(id: Int, occurrence: TaskOccurrenceId?, expectedDate: LocalDate?, date: LocalDate, scope: TaskChangeScope): Boolean =
        if (occurrence == null) reschedule(id, expectedDate, date) else false
    suspend fun deleteOccurrence(id: Int, occurrence: TaskOccurrenceId?, scope: TaskChangeScope): Boolean {
        if (occurrence != null && scope != TaskChangeScope.EntireTask) return false
        delete(id)
        return true
    }
}
