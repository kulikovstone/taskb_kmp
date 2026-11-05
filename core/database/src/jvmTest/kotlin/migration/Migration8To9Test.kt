package com.mkulikov.taskb.core.database.migration

import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.database.useDatabase
import com.mkulikov.taskb.feature.event.api.database.EventEntity
import com.mkulikov.taskb.feature.event.api.database.EventOccurrenceEntity
import com.mkulikov.taskb.feature.event.api.database.EventScheduleEntity
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.feature.task.api.database.TaskOccurrenceEntity
import com.mkulikov.taskb.feature.task.api.database.TaskScheduleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.test.*

class Migration8To9Test {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `one-off migration preserves all event fields existing events and untimed tasks without duplicates on restart`() = runBlocking<Unit> {
        val file = versionEight("one-off.db") { connection ->
            connection.execSQL("INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
            connection.execSQL("INSERT INTO groups VALUES (7, 'Work', 123)")
            connection.execSQL("""
                INSERT INTO tasks VALUES
                (1, 'Appointment', 20760, 7, 1000, NULL, 0, 'Line one' || char(10) || 'Line two', 570, 90),
                (2, 'Completed appointment', 20759, NULL, 2000, 3000, 0, 'Keep this', 1410, 1560),
                (3, 'Untimed', 20760, 7, 4000, 5000, 0, 'Still a task', NULL, NULL),
                (4, 'No date', NULL, NULL, 6000, NULL, 0, '', NULL, NULL),
                (5, 'Legacy hour', 20761, NULL, 7000, NULL, 0, '', 600, NULL)
            """.trimIndent())
            // IDs overlap with the source task and schedule namespaces intentionally.
            connection.execSQL("INSERT INTO events VALUES (1, 'Existing event', 20760, 7, 100, 1, 'Original', 540, 30)")
            connection.execSQL("INSERT INTO event_schedules VALUES (1, 1, 20760, 'Weekly', 2, 5, 1, 1, NULL, 4, NULL, 540, 30)")
            connection.execSQL("INSERT INTO event_occurrences VALUES (1, 20760, 1, 20762, 0, 1, 660, 45)")
        }
        repeat(2) {
            AppDatabase.open(file).useDatabase { database ->
                val events = database.eventDao().observeRecords().first()
                assertEquals(4, events.size)
                val appointment = events.single { it.event.name == "Appointment" }.event
                assertEquals(EventEntity(appointment.id, "Appointment", 20760, 7, 1000, false, "Line one\nLine two", 570, 90), appointment)
                val completed = events.single { it.event.name == "Completed appointment" }.event
                assertEquals(EventEntity(completed.id, "Completed appointment", 20759, null, 2000, false, "Keep this", 1410, 1560), completed)
                val legacy = events.single { it.event.name == "Legacy hour" }.event
                assertEquals(600, legacy.startTimeMinutes)
                assertNull(legacy.durationMinutes)
                val existing = assertNotNull(database.eventDao().getRecord(1))
                assertEquals(EventEntity(1, "Existing event", 20760, 7, 100, true, "Original", 540, 30), existing.event)
                assertEquals(EventScheduleEntity(1, 1, 20760, "Weekly", 2, 5, 1, 1, occurrenceCount = 4, startTimeMinutes = 540, durationMinutes = 30), existing.schedules.single())
                assertEquals(EventOccurrenceEntity(1, 20760, 1, 20762, false, true, 660, 45), existing.occurrences.single())
                assertEquals(listOf(3, 4), database.taskDao().observeTasks().first().map { it.id })
                assertEquals(TaskEntity(3, "Untimed", 20760, 7, 4000, 5000, false, "Still a task"), database.taskDao().getById(3))
                assertEquals(TaskEntity(4, "No date", createdAt = 6000), database.taskDao().getById(4))
                assertEquals("true", database.settingsDao().getValue("welcomeCompleted"))
                assertEquals(10L, database.queryLong("PRAGMA user_version"))
                database.assertForeignKeysValid()
            }
        }
    }

    @Test
    fun `timing in any schedule or explicit exception transfers the complete series and preserves mixed history`() = runBlocking<Unit> {
        val file = versionEight("series.db") { connection ->
            connection.execSQL("INSERT INTO groups VALUES (7, 'Work', 123)")
            connection.execSQL("""
                INSERT INTO tasks VALUES
                (10, 'Mixed history', 20760, 7, 1000, 2000, 1, 'All revisions', NULL, NULL),
                (20, 'Timed exception', 20760, NULL, 3000, NULL, 1, 'Override', NULL, NULL),
                (30, 'Untimed series', 20760, NULL, 4000, 5000, 1, 'Leave unchanged', NULL, NULL),
                (40, 'Base time', 20760, NULL, 6000, NULL, 1, '', 720, 60)
            """.trimIndent())
            connection.execSQL("""
                INSERT INTO task_schedules VALUES
                (1, 10, 20750, 'Weekly', 2, 5, 1, 1, 20759, NULL, 20760, 540, 90),
                (2, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, 10, NULL, NULL, NULL),
                (3, 20, 20760, 'Daily', 1, 0, 1, 1, NULL, 5, NULL, NULL, NULL),
                (4, 30, 20760, 'Monthly', 1, 0, 31, 1, NULL, 6, NULL, NULL, NULL),
                (5, 40, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, NULL, NULL, NULL)
            """.trimIndent())
            connection.execSQL("""
                INSERT INTO task_occurrences VALUES
                (1, 20750, 10, 20752, 2500, 0, 1, 600, 30),
                (1, 20751, 10, NULL, NULL, 1, 0, NULL, NULL),
                (1, 20753, 10, NULL, 2600, 0, 1, NULL, NULL),
                (2, 20760, 10, 20762, NULL, 0, 0, NULL, NULL),
                (3, 20761, 20, 20770, 3500, 0, 1, 1430, 120),
                (3, 20762, 20, NULL, NULL, 1, 0, NULL, NULL),
                (4, 20760, 30, 20765, 4500, 0, 0, 900, 60)
            """.trimIndent())
            connection.execSQL("INSERT INTO events VALUES (10, 'Existing event', 20760, NULL, 100, 1, '', 480, 60)")
            connection.execSQL("INSERT INTO event_schedules VALUES (1, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, 2, NULL, 480, 60)")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertEquals(listOf(30), database.taskDao().observeTasks().first().map { it.id })
            val untimed = assertNotNull(database.taskDao().getRecord(30))
            assertEquals(TaskEntity(30, "Untimed series", 20760, null, 4000, 5000, true, "Leave unchanged"), untimed.task)
            assertEquals(TaskScheduleEntity(4, 30, 20760, "Monthly", 1, 0, 31, 1, occurrenceCount = 6), untimed.schedules.single())
            // A value in an exception is inert without overridesTiming; it must not convert this series.
            assertEquals(TaskOccurrenceEntity(4, 20760, 30, 20765, 4500, false), untimed.occurrences.single())
            val events = database.eventDao().observeRecords().first()
            assertEquals(4, events.size)
            val mixed = events.single { it.event.name == "Mixed history" }
            assertEquals(7, mixed.event.groupId)
            assertEquals(1000L, mixed.event.createdAt)
            assertEquals("All revisions", mixed.event.description)
            assertNull(mixed.event.startTimeMinutes)
            val timed = mixed.schedules.single { it.startTimeMinutes != null }
            val noTime = mixed.schedules.single { it.startTimeMinutes == null }
            assertEquals(EventScheduleEntity(timed.id, mixed.event.id, 20750, "Weekly", 2, 5, 1, 1, 20759, null, 20760, 540, 90), timed)
            assertEquals(EventScheduleEntity(noTime.id, mixed.event.id, 20760, "Daily", 1, 0, 1, 1, occurrenceCount = 10), noTime)
            assertEquals(setOf(
                EventOccurrenceEntity(timed.id, 20750, mixed.event.id, 20752, false, true, 600, 30),
                EventOccurrenceEntity(timed.id, 20751, mixed.event.id, skipped = true),
                EventOccurrenceEntity(timed.id, 20753, mixed.event.id, overridesTiming = true),
                EventOccurrenceEntity(noTime.id, 20760, mixed.event.id, 20762),
            ), mixed.occurrences.toSet())
            val exception = events.single { it.event.name == "Timed exception" }
            val schedule = exception.schedules.single()
            assertEquals(5, schedule.occurrenceCount)
            assertNull(schedule.startTimeMinutes)
            assertEquals(setOf(
                EventOccurrenceEntity(schedule.id, 20761, exception.event.id, 20770, false, true, 1430, 120),
                EventOccurrenceEntity(schedule.id, 20762, exception.event.id, skipped = true),
            ), exception.occurrences.toSet())
            assertEquals(720, events.single { it.event.name == "Base time" }.event.startTimeMinutes)
            assertEquals(10, database.eventDao().getRecord(10)?.event?.id)
            assertEquals(10, database.eventDao().getRecord(10)?.schedules?.single()?.eventId)
            assertEquals(1L, database.queryLong("SELECT COUNT(*) FROM task_schedules"))
            assertEquals(1L, database.queryLong("SELECT COUNT(*) FROM task_occurrences"))
            database.assertForeignKeysValid()
        }
    }

    @Test
    fun `failed conversion rolls back all copies deletions and schema version and can be retried`() = runBlocking<Unit> {
        val file = versionEight("rollback.db") { connection ->
            connection.execSQL("INSERT INTO tasks VALUES (1, 'First', 20760, NULL, 1000, NULL, 1, '', 540, 60), (2, 'Fail migration', 20761, NULL, 2000, NULL, 0, '', 600, 30)")
            connection.execSQL("INSERT INTO task_schedules VALUES (1, 1, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, NULL, 540, 60)")
            connection.execSQL("INSERT INTO task_occurrences VALUES (1, 20760, 1, 20762, 1500, 0, 1, 660, 30)")
            connection.execSQL("INSERT INTO events VALUES (1, 'Existing', 20760, NULL, 100, 0, '', NULL, NULL)")
            connection.execSQL("CREATE TRIGGER fail_conversion BEFORE INSERT ON events WHEN NEW.name = 'Fail migration' BEGIN SELECT RAISE(ABORT, 'Simulated migration failure'); END")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertFailsWith<SQLiteException> { database.eventDao().observeRecords().first() }
        }
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            assertEquals(8L, connection.queryLong("PRAGMA user_version"))
            assertEquals(2L, connection.queryLong("SELECT COUNT(*) FROM tasks"))
            assertEquals(1L, connection.queryLong("SELECT COUNT(*) FROM task_schedules"))
            assertEquals(1L, connection.queryLong("SELECT COUNT(*) FROM task_occurrences"))
            assertEquals(1L, connection.queryLong("SELECT COUNT(*) FROM events"))
            assertEquals(0L, connection.queryLong("SELECT COUNT(*) FROM event_schedules"))
            assertEquals(0L, connection.queryLong("SELECT COUNT(*) FROM event_occurrences"))
            connection.execSQL("DROP TRIGGER fail_conversion")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertEquals(3, database.eventDao().observeRecords().first().size)
            assertTrue(database.taskDao().observeTasks().first().isEmpty())
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
            database.assertForeignKeysValid()
        }
    }

    @Test
    fun `upgrade without timed tasks leaves tasks and events unchanged`() = runBlocking<Unit> {
        val file = versionEight("untimed.db") { connection ->
            connection.execSQL("INSERT INTO tasks VALUES (1, 'Task', 20760, NULL, 1000, 2000, 0, 'Untimed', NULL, NULL)")
            connection.execSQL("INSERT INTO events VALUES (1, 'Event', 20760, NULL, 3000, 0, 'Existing', 540, 60)")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertEquals(TaskEntity(1, "Task", 20760, null, 1000, 2000, false, "Untimed"), database.taskDao().getById(1))
            assertEquals(EventEntity(1, "Event", 20760, null, 3000, false, "Existing", 540, 60), database.eventDao().observeRecords().first().single().event)
            database.assertForeignKeysValid()
        }
    }

    private fun versionEight(name: String, seed: (SQLiteConnection) -> Unit): Path {
        val file = directory.root.toPath().resolve(name)
        val schema = checkNotNull(javaClass.getResource("/database/schema-8.sql")).readText()
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("PRAGMA foreign_keys = ON")
            schema.lineSequence().filter { it.isNotBlank() && !it.startsWith("--") }.forEach(connection::execSQL)
            seed(connection)
        }
        return file
    }

    private fun SQLiteConnection.queryLong(sql: String): Long = prepare(sql).use { statement ->
        check(statement.step())
        statement.getLong(0)
    }

    private suspend fun AppDatabase.queryLong(sql: String): Long = useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            check(statement.step())
            statement.getLong(0)
        }
    }

    private suspend fun AppDatabase.assertForeignKeysValid() = useReaderConnection { connection ->
        connection.usePrepared("PRAGMA foreign_key_check") { statement -> assertFalse(statement.step()) }
    }
}
