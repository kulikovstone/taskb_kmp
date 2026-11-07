package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration9To10 : Migration(startVersion = 9, endVersion = 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Version 9 still allowed creating timed tasks. Transfer those before removing their fields.
        Migration8To9().migrate(connection = connection)
        for (table in listOf("tasks", "task_schedules", "task_occurrences")) {
            connection.execSQL(sql = "ALTER TABLE $table DROP COLUMN startTimeMinutes")
            connection.execSQL(sql = "ALTER TABLE $table DROP COLUMN durationMinutes")
        }
        connection.execSQL(sql = "ALTER TABLE task_occurrences DROP COLUMN overridesTiming")
    }
}
