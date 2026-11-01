package com.mkulikov.taskb.feature.task.api.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.OnConflictStrategy
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ITaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY id")
    fun observeRecords(): Flow<List<TaskRecord>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getRecord(id: Int): TaskRecord?

    @Insert
    suspend fun insertSchedule(schedule: TaskScheduleEntity): Long

    @Query("UPDATE task_schedules SET closedBefore = :before WHERE id = :id")
    suspend fun closeSchedule(id: Long, before: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOccurrence(occurrence: TaskOccurrenceEntity)

    @Query("DELETE FROM task_occurrences WHERE scheduleId = :scheduleId AND scheduledDate >= :fromDate")
    suspend fun deleteOccurrencesFrom(scheduleId: Long, fromDate: Long)

    @Query("UPDATE tasks SET recurring = :recurring WHERE id = :id")
    suspend fun setRecurring(id: Int, recurring: Boolean)

    @Query("SELECT * FROM tasks ORDER BY id")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity): Int

    @Query("UPDATE tasks SET name = :name, dueDate = :dueDate, groupId = :groupId WHERE id = :id")
    suspend fun updateDetails(id: Int, name: String, dueDate: Long?, groupId: Int?): Int

    @Query("UPDATE tasks SET dueDate = :dueDate WHERE id = :id AND dueDate IS :expectedDate")
    suspend fun reschedule(id: Int, expectedDate: Long?, dueDate: Long?): Int

    @Query("""
        UPDATE tasks SET completedAt = :completedAt
        WHERE id = :id AND (completedAt IS NULL OR :completedAt IS NULL)
    """)
    suspend fun setCompletedAt(id: Int, completedAt: Long?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Int)
}
