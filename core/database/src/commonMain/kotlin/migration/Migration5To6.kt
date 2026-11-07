package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration5To6 : Migration(startVersion = 5, endVersion = 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(sql = "ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''")
    }
}
