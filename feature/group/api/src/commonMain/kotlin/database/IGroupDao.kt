package com.mkulikov.taskb.feature.group.api.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IGroupDao {
    @Query(value = "SELECT * FROM `groups` ORDER BY id") fun observeGroups(): Flow<List<GroupEntity>>

    @Query(
        value = """
            SELECT g.*, COUNT(CASE WHEN t.completedAt IS NULL THEN t.id END) AS planned,
                COUNT(t.completedAt) AS completed,
                (SELECT COUNT(*) FROM events e WHERE e.groupId = g.id) AS eventCount
            FROM `groups` g LEFT JOIN tasks t ON t.groupId = g.id GROUP BY g.id ORDER BY g.id
        """,
    )
    fun observeGroupsWithTaskCounts(): Flow<List<GroupWithTaskCounts>>

    @Query(
        value = """
            SELECT g.*, COUNT(CASE WHEN t.completedAt IS NULL THEN t.id END) AS planned,
                COUNT(t.completedAt) AS completed,
                (SELECT COUNT(*) FROM events e WHERE e.groupId = g.id) AS eventCount,
                COUNT(CASE WHEN t.createdAt >= :createdSince THEN t.id END) AS recentTaskCount
            FROM `groups` g LEFT JOIN tasks t ON t.groupId = g.id GROUP BY g.id
        """,
    )
    fun observeGroupsWithUsage(createdSince: Long): Flow<List<GroupWithUsage>>

    @Query(
        value = """
            SELECT g.*, COUNT(CASE WHEN t.completedAt IS NULL THEN t.id END) AS planned,
                COUNT(t.completedAt) AS completed,
                (SELECT COUNT(*) FROM events e WHERE e.groupId = g.id) AS eventCount
            FROM `groups` g LEFT JOIN tasks t ON t.groupId = g.id WHERE g.id = :id GROUP BY g.id
        """,
    )
    suspend fun getWithTaskCounts(id: Int): GroupWithTaskCounts?

    @Query(value = "SELECT * FROM `groups` WHERE id = :id LIMIT 1") suspend fun getById(id: Int): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(group: GroupEntity): Long

    @Update(onConflict = OnConflictStrategy.IGNORE) suspend fun update(group: GroupEntity): Int

    @Query(value = "DELETE FROM `groups` WHERE id = :id") suspend fun deleteById(id: Int)
}
