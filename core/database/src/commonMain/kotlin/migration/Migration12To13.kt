package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration12To13 : Migration(startVersion = 12, endVersion = 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(sql = "ALTER TABLE tasks ADD COLUMN showInCalendar INTEGER NOT NULL DEFAULT 1")
        // Match the task model's schedule selection, including the last schedule of an ended series.
        connection.execSQL(
            sql = """
                UPDATE tasks SET showInCalendar = 0
                WHERE recurring = 1 AND (
                    SELECT frequency = 'Daily' AND interval = 1
                    FROM task_schedules
                    WHERE taskId = tasks.id
                    ORDER BY closedBefore IS NULL DESC, id DESC
                    LIMIT 1
                )
            """
                .trimIndent(),
        )
    }
}
