package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration11To12 : Migration(startVersion = 11, endVersion = 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            sql = """
            CREATE TABLE IF NOT EXISTS holiday_months (
                locationKey TEXT NOT NULL,
                year INTEGER NOT NULL,
                month INTEGER NOT NULL,
                fetchedAt INTEGER NOT NULL,
                PRIMARY KEY(locationKey, year, month)
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            sql = """
            CREATE TABLE IF NOT EXISTS holidays (
                locationKey TEXT NOT NULL,
                year INTEGER NOT NULL,
                month INTEGER NOT NULL,
                id TEXT NOT NULL,
                date INTEGER NOT NULL,
                name TEXT NOT NULL,
                countryCode TEXT NOT NULL,
                PRIMARY KEY(locationKey, year, month, id),
                FOREIGN KEY(locationKey, year, month) REFERENCES holiday_months(locationKey, year, month)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent()
        )
    }
}
