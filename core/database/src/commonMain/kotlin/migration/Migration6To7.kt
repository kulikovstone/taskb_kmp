package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration6To7 : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        for (table in listOf("tasks", "task_schedules", "task_occurrences")) {
            connection.execSQL("ALTER TABLE $table ADD COLUMN startTimeMinutes INTEGER")
            connection.execSQL("ALTER TABLE $table ADD COLUMN durationMinutes INTEGER")
        }
        connection.execSQL("ALTER TABLE task_occurrences ADD COLUMN overridesTiming INTEGER NOT NULL DEFAULT 0")
    }
}
