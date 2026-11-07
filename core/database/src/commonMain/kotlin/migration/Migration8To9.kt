package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Converts entire task series, including their history, when any revision has a time. */
internal class Migration8To9 : Migration(startVersion = 8, endVersion = 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Read and close cursors before writing to the same tables. Room runs the upgrade in a transaction.
        val taskIds = connection
            .prepare(
                sql = """
                SELECT t.id FROM tasks t
                WHERE t.startTimeMinutes IS NOT NULL
                    OR EXISTS (
                        SELECT 1 FROM task_schedules s
                        WHERE s.taskId = t.id AND s.startTimeMinutes IS NOT NULL
                    )
                    OR EXISTS (
                        SELECT 1 FROM task_occurrences o
                        WHERE o.taskId = t.id AND o.overridesTiming = 1 AND o.startTimeMinutes IS NOT NULL
                    )
                ORDER BY t.id
                """
                    .trimIndent()
            )
            .use { statement ->
                buildList { while (statement.step()) add(element = statement.getLong(index = 0)) }
            }

        for (taskId in taskIds) {
            connection
                .prepare(
                    sql = """
                    INSERT INTO events (
                        name, dueDate, groupId, createdAt, recurring, description,
                        startTimeMinutes, durationMinutes
                    )
                    SELECT name, dueDate, groupId, createdAt, recurring, description,
                        startTimeMinutes, durationMinutes
                    FROM tasks WHERE id = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.bindLong(index = 1, value = taskId)
                    statement.step()
                }
            val eventId = insertedId(connection = connection)

            val scheduleIds = connection.prepare(
                sql = "SELECT id FROM task_schedules WHERE taskId = ? ORDER BY id",
            ).use {
                statement ->
                statement.bindLong(index = 1, value = taskId)
                buildList { while (statement.step()) add(element = statement.getLong(index = 0)) }
            }
            for (scheduleId in scheduleIds) copySchedule(
                connection = connection,
                scheduleId = scheduleId,
                eventId = eventId,
            )

            // Delete only after all schedules and exceptions have been copied successfully.
            connection.prepare(sql = "DELETE FROM task_occurrences WHERE taskId = ?").use { statement ->
                statement.bindLong(index = 1, value = taskId)
                statement.step()
            }
            connection.prepare(sql = "DELETE FROM task_schedules WHERE taskId = ?").use { statement ->
                statement.bindLong(index = 1, value = taskId)
                statement.step()
            }
            connection.prepare(sql = "DELETE FROM tasks WHERE id = ?").use { statement ->
                statement.bindLong(index = 1, value = taskId)
                statement.step()
            }
        }
    }

    private fun copySchedule(connection: SQLiteConnection, scheduleId: Long, eventId: Long) {
        // Let SQLite allocate IDs so existing events and their schedules can never be overwritten.
        connection
            .prepare(
                sql = """
                INSERT INTO event_schedules (
                    eventId, startDate, frequency, interval, weekdays, dayOfMonth, month,
                    endDate, occurrenceCount, closedBefore, startTimeMinutes, durationMinutes
                )
                SELECT ?, startDate, frequency, interval, weekdays, dayOfMonth, month,
                    endDate, occurrenceCount, closedBefore, startTimeMinutes, durationMinutes
                FROM task_schedules WHERE id = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.bindLong(index = 1, value = eventId)
                statement.bindLong(index = 2, value = scheduleId)
                statement.step()
            }
        val eventScheduleId = insertedId(connection = connection)
        // Completion is deliberately omitted: events have no completion state.
        connection
            .prepare(
                sql = """
                INSERT INTO event_occurrences (
                    scheduleId, scheduledDate, eventId, movedDate, skipped,
                    overridesTiming, startTimeMinutes, durationMinutes
                )
                SELECT ?, scheduledDate, ?, movedDate, skipped,
                    overridesTiming, startTimeMinutes, durationMinutes
                FROM task_occurrences WHERE scheduleId = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.bindLong(index = 1, value = eventScheduleId)
                statement.bindLong(index = 2, value = eventId)
                statement.bindLong(index = 3, value = scheduleId)
                statement.step()
            }
    }

    private fun insertedId(connection: SQLiteConnection): Long {
        return connection.prepare(
            sql = "SELECT last_insert_rowid()",
        ).use { statement ->
            check(value = statement.step())
            statement.getLong(index = 0)
        }
    }
}
