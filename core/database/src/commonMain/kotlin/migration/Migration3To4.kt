package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlin.time.Clock

internal class Migration3To4 : Migration(startVersion = 3, endVersion = 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // The previous schema did not record timestamps. Preserve completion using migration time.
        val migratedAt = Clock.System.now().toEpochMilliseconds()
        connection.execSQL(
            sql = """
            CREATE TABLE tasks_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                dueDate INTEGER,
                groupId INTEGER,
                createdAt INTEGER NOT NULL,
                completedAt INTEGER,
                FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """
                .trimIndent()
        )
        connection
            .prepare(
                sql = """
                INSERT INTO tasks_new (id, name, dueDate, groupId, createdAt, completedAt)
                SELECT id, name, dueDate, groupId, ?, CASE WHEN completed != 0 THEN ? ELSE NULL END
                FROM tasks
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.bindLong(index = 1, value = migratedAt)
                statement.bindLong(index = 2, value = migratedAt)
                statement.step()
            }
        connection.execSQL(sql = "DROP TABLE tasks")
        connection.execSQL(sql = "ALTER TABLE tasks_new RENAME TO tasks")
        connection.execSQL(sql = "CREATE INDEX index_tasks_groupId ON tasks (groupId)")
    }
}
