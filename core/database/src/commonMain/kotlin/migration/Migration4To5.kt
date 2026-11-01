package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration4To5 : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE tasks ADD COLUMN recurring INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS task_schedules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                taskId INTEGER NOT NULL, startDate INTEGER NOT NULL,
                frequency TEXT NOT NULL, interval INTEGER NOT NULL, weekdays INTEGER NOT NULL,
                dayOfMonth INTEGER NOT NULL, month INTEGER NOT NULL, endDate INTEGER,
                occurrenceCount INTEGER, closedBefore INTEGER,
                FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_schedules_taskId ON task_schedules (taskId)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS task_occurrences (
                scheduleId INTEGER NOT NULL, scheduledDate INTEGER NOT NULL, taskId INTEGER NOT NULL,
                movedDate INTEGER, completedAt INTEGER, skipped INTEGER NOT NULL,
                PRIMARY KEY(scheduleId, scheduledDate),
                FOREIGN KEY(scheduleId) REFERENCES task_schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_occurrences_taskId ON task_occurrences (taskId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_occurrences_scheduleId ON task_occurrences (scheduleId)")
    }
}
