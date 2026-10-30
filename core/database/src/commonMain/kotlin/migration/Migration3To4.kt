package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlin.time.Clock

internal class Migration3To4 : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // The previous schema did not record timestamps. Preserve completion using migration time.
        val migratedAt = Clock.System.now().toEpochMilliseconds()
        connection.execSQL("""
            CREATE TABLE tasks_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                dueDate INTEGER,
                groupId INTEGER,
                createdAt INTEGER NOT NULL,
                completedAt INTEGER,
                FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        connection.prepare("""
            INSERT INTO tasks_new (id, name, dueDate, groupId, createdAt, completedAt)
            SELECT id, name, dueDate, groupId, ?, CASE WHEN completed != 0 THEN ? ELSE NULL END
            FROM tasks
        """.trimIndent()).use { statement ->
            statement.bindLong(1, migratedAt)
            statement.bindLong(2, migratedAt)
            statement.step()
        }
        connection.execSQL("DROP TABLE tasks")
        connection.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
        connection.execSQL("CREATE INDEX index_tasks_groupId ON tasks (groupId)")
    }
}
