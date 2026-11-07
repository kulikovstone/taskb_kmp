package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration6To7 : Migration(startVersion = 6, endVersion = 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        for (table in listOf("tasks", "task_schedules", "task_occurrences")) {
            connection.execSQL(sql = "ALTER TABLE $table ADD COLUMN startTimeMinutes INTEGER")
            connection.execSQL(sql = "ALTER TABLE $table ADD COLUMN durationMinutes INTEGER")
        }
        connection.execSQL(
            sql = "ALTER TABLE task_occurrences ADD COLUMN overridesTiming INTEGER NOT NULL DEFAULT 0"
        )
    }
}
