package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration10To11 : Migration(startVersion = 10, endVersion = 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        for (table in listOf("events", "event_schedules", "event_occurrences")) {
            connection.execSQL(sql = "ALTER TABLE $table ADD COLUMN durationDays INTEGER NOT NULL DEFAULT 1")
        }
    }
}
