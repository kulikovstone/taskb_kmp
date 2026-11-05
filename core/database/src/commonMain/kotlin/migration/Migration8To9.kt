package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Converts entire task series, including their history, when any revision has a time. */
internal class Migration8To9 : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Read and close cursors before writing to the same tables. Room runs the upgrade in a transaction.
        val taskIds = connection.prepare("""
            SELECT t.id FROM tasks t
            WHERE t.startTimeMinutes IS NOT NULL
                OR EXISTS (SELECT 1 FROM task_schedules s WHERE s.taskId = t.id AND s.startTimeMinutes IS NOT NULL)
                OR EXISTS (
                    SELECT 1 FROM task_occurrences o
                    WHERE o.taskId = t.id AND o.overridesTiming = 1 AND o.startTimeMinutes IS NOT NULL
                )
            ORDER BY t.id
        """.trimIndent()).use { statement -> buildList { while (statement.step()) add(statement.getLong(0)) } }

        for (taskId in taskIds) {
            connection.prepare("""
                INSERT INTO events (name, dueDate, groupId, createdAt, recurring, description, startTimeMinutes, durationMinutes)
                SELECT name, dueDate, groupId, createdAt, recurring, description, startTimeMinutes, durationMinutes
                FROM tasks WHERE id = ?
            """.trimIndent()).use { statement ->
                statement.bindLong(1, taskId)
                statement.step()
            }
            val eventId = insertedId(connection)

            val scheduleIds = connection.prepare("SELECT id FROM task_schedules WHERE taskId = ? ORDER BY id").use { statement ->
                statement.bindLong(1, taskId)
                buildList { while (statement.step()) add(statement.getLong(0)) }
            }
            for (scheduleId in scheduleIds) copySchedule(connection, scheduleId, eventId)

            // Delete only after all schedules and exceptions have been copied successfully.
            connection.prepare("DELETE FROM task_occurrences WHERE taskId = ?").use { statement ->
                statement.bindLong(1, taskId)
                statement.step()
            }
            connection.prepare("DELETE FROM task_schedules WHERE taskId = ?").use { statement ->
                statement.bindLong(1, taskId)
                statement.step()
            }
            connection.prepare("DELETE FROM tasks WHERE id = ?").use { statement ->
                statement.bindLong(1, taskId)
                statement.step()
            }
        }
    }

    private fun copySchedule(connection: SQLiteConnection, scheduleId: Long, eventId: Long) {
        // Let SQLite allocate IDs so existing events and their schedules can never be overwritten.
        connection.prepare("""
            INSERT INTO event_schedules (
                eventId, startDate, frequency, interval, weekdays, dayOfMonth, month,
                endDate, occurrenceCount, closedBefore, startTimeMinutes, durationMinutes
            )
            SELECT ?, startDate, frequency, interval, weekdays, dayOfMonth, month,
                endDate, occurrenceCount, closedBefore, startTimeMinutes, durationMinutes
            FROM task_schedules WHERE id = ?
        """.trimIndent()).use { statement ->
            statement.bindLong(1, eventId)
            statement.bindLong(2, scheduleId)
            statement.step()
        }
        val eventScheduleId = insertedId(connection)
        // Completion is deliberately omitted: events have no completion state.
        connection.prepare("""
            INSERT INTO event_occurrences (
                scheduleId, scheduledDate, eventId, movedDate, skipped,
                overridesTiming, startTimeMinutes, durationMinutes
            )
            SELECT ?, scheduledDate, ?, movedDate, skipped,
                overridesTiming, startTimeMinutes, durationMinutes
            FROM task_occurrences WHERE scheduleId = ?
        """.trimIndent()).use { statement ->
            statement.bindLong(1, eventScheduleId)
            statement.bindLong(2, eventId)
            statement.bindLong(3, scheduleId)
            statement.step()
        }
    }

    private fun insertedId(connection: SQLiteConnection): Long =
        connection.prepare("SELECT last_insert_rowid()").use { statement ->
            check(statement.step())
            statement.getLong(0)
        }
}
