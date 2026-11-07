package com.mkulikov.taskb.feature.event.api.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IEventDao {
    @Transaction @Query(value = "SELECT * FROM events ORDER BY id") fun observeRecords(): Flow<List<EventRecord>>

    @Transaction
    @Query(value = "SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getRecord(id: Int): EventRecord?

    @Insert suspend fun insertSchedule(schedule: EventScheduleEntity): Long

    @Query(value = "UPDATE event_schedules SET closedBefore = :before WHERE id = :id")
    suspend fun closeSchedule(id: Long, before: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveOccurrence(occurrence: EventOccurrenceEntity)

    @Query(value = "DELETE FROM event_occurrences WHERE scheduleId = :scheduleId AND scheduledDate >= :fromDate")
    suspend fun deleteOccurrencesFrom(scheduleId: Long, fromDate: Long)

    @Query(value = "UPDATE events SET recurring = :recurring WHERE id = :id")
    suspend fun setRecurring(id: Int, recurring: Boolean)

    @Query(value = "SELECT * FROM events ORDER BY id") fun observeEvents(): Flow<List<EventEntity>>

    @Query(value = "SELECT * FROM events WHERE id = :id LIMIT 1") suspend fun getById(id: Int): EventEntity?

    @Insert suspend fun insert(event: EventEntity): Long

    @Update suspend fun update(event: EventEntity): Int

    @Query(
        value =
            ("UPDATE events SET name = :name, dueDate = :dueDate, groupId = :groupId, description = " +
                ":description, startTimeMinutes = :startTimeMinutes, durationMinutes = :durationMinutes, " +
                "durationDays = :durationDays WHERE id = :id")
    )
    suspend fun updateDetails(
        id: Int,
        name: String,
        dueDate: Long?,
        groupId: Int?,
        description: String,
        startTimeMinutes: Int?,
        durationMinutes: Int?,
        durationDays: Int,
    ): Int

    @Query(
        value =
            ("UPDATE events SET dueDate = :dueDate, startTimeMinutes = CASE WHEN :dueDate IS NULL THEN " +
                "NULL ELSE startTimeMinutes END, durationMinutes = CASE WHEN :dueDate IS NULL THEN NULL ELSE " +
                "durationMinutes END WHERE id = :id AND dueDate IS :expectedDate")
    )
    suspend fun reschedule(id: Int, expectedDate: Long?, dueDate: Long?): Int

    @Query(value = "DELETE FROM events WHERE id = :id") suspend fun deleteById(id: Int)
}
