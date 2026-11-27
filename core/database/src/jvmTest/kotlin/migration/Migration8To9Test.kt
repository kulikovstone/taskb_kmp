package com.mkulikov.taskb.core.database.migration

import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.database.useDatabase
import com.mkulikov.taskb.feature.item.api.database.EventEntity
import com.mkulikov.taskb.feature.item.api.database.EventOccurrenceEntity
import com.mkulikov.taskb.feature.item.api.database.EventScheduleEntity
import com.mkulikov.taskb.feature.item.api.database.TaskEntity
import com.mkulikov.taskb.feature.item.api.database.TaskOccurrenceEntity
import com.mkulikov.taskb.feature.item.api.database.TaskScheduleEntity
import java.nio.file.Path
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class Migration8To9Test {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `one-off migration preserves event fields existing events and untimed tasks without duplicates`() {
        runBlocking<Unit> {
            val file = versionEight(name = "one-off.db") { connection ->
                connection.execSQL(sql = "INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
                connection.execSQL(sql = "INSERT INTO groups VALUES (7, 'Work', 123)")
                connection.execSQL(
                    sql = """
                    INSERT INTO tasks VALUES
                    (1, 'Appointment', 20760, 7, 1000, NULL, 0, 'Line one' || char(10) || 'Line two', 570, 90),
                    (2, 'Completed appointment', 20759, NULL, 2000, 3000, 0, 'Keep this', 1410, 1560),
                    (3, 'Untimed', 20760, 7, 4000, 5000, 0, 'Still a task', NULL, NULL),
                    (4, 'No date', NULL, NULL, 6000, NULL, 0, '', NULL, NULL),
                    (5, 'Legacy hour', 20761, NULL, 7000, NULL, 0, '', 600, NULL)
                    """
                        .trimIndent()
                )
                // IDs overlap with the source task and schedule namespaces intentionally.
                connection.execSQL(
                    sql =
                        "INSERT INTO events VALUES (1, 'Existing event', 20760, 7, 100, 1, 'Original', 540, 30)"
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO event_schedules VALUES (1, 1, 20760, 'Weekly', 2, 5, 1, 1, " +
                            "NULL, 4, NULL, 540, 30)")
                )
                connection.execSQL(
                    sql = "INSERT INTO event_occurrences VALUES (1, 20760, 1, 20762, 0, 1, 660, 45)"
                )
            }
            repeat(times = 2) {
                AppDatabase.open(file = file).useDatabase { database ->
                    val events = database.eventDao().observeRecords().first()
                    assertEquals(expected = 4, actual = events.size)
                    val appointment = events.single { record -> record.event.name == "Appointment" }.event
                    assertEquals(
                        expected = EventEntity(
                            id = appointment.id,
                            name = "Appointment",
                            dueDate = 20760,
                            groupId = 7,
                            createdAt = 1000,
                            recurring = false,
                            description = "Line one\nLine two",
                            startTimeMinutes = 570,
                            durationMinutes = 90,
                        ),
                        actual = appointment,
                    )
                    val completed = events.single { record -> record.event.name == "Completed appointment" }.event
                    assertEquals(
                        expected = EventEntity(
                            id = completed.id,
                            name = "Completed appointment",
                            dueDate = 20759,
                            groupId = null,
                            createdAt = 2000,
                            recurring = false,
                            description = "Keep this",
                            startTimeMinutes = 1410,
                            durationMinutes = 1560,
                        ),
                        actual = completed,
                    )
                    val legacy = events.single { record -> record.event.name == "Legacy hour" }.event
                    assertEquals(expected = 600, actual = legacy.startTimeMinutes)
                    assertNull(actual = legacy.durationMinutes)
                    val existing = assertNotNull(actual = database.eventDao().getRecord(id = 1))
                    assertEquals(
                        expected = EventEntity(
                            id = 1,
                            name = "Existing event",
                            dueDate = 20760,
                            groupId = 7,
                            createdAt = 100,
                            recurring = true,
                            description = "Original",
                            startTimeMinutes = 540,
                            durationMinutes = 30,
                        ),
                        actual = existing.event,
                    )
                    assertEquals(
                        expected = EventScheduleEntity(
                            id = 1,
                            eventId = 1,
                            startDate = 20760,
                            frequency = "Weekly",
                            interval = 2,
                            weekdays = 5,
                            dayOfMonth = 1,
                            month = 1,
                            occurrenceCount = 4,
                            startTimeMinutes = 540,
                            durationMinutes = 30,
                        ),
                        actual = existing.schedules.single(),
                    )
                    assertEquals(
                        expected = EventOccurrenceEntity(
                            scheduleId = 1,
                            scheduledDate = 20760,
                            eventId = 1,
                            movedDate = 20762,
                            skipped = false,
                            overridesTiming = true,
                            startTimeMinutes = 660,
                            durationMinutes = 45,
                        ),
                        actual = existing.occurrences.single(),
                    )
                    assertEquals(
                        expected = listOf(3, 4),
                        actual = database.taskDao().observeTasks().first().map(transform = TaskEntity::id),
                    )
                    assertEquals(
                        expected = TaskEntity(
                            id = 3,
                            name = "Untimed",
                            dueDate = 20760,
                            groupId = 7,
                            createdAt = 4000,
                            completedAt = 5000,
                            recurring = false,
                            description = "Still a task",
                        ),
                        actual = database.taskDao().getById(id = 3),
                    )
                    assertEquals(
                        expected = TaskEntity(id = 4, name = "No date", createdAt = 6000),
                        actual = database.taskDao().getById(id = 4),
                    )
                    assertEquals(
                        expected = "true",
                        actual = database.settingsDao().getValue(key = "welcomeCompleted"),
                    )
                    assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
                    database.assertForeignKeysValid()
                }
            }
        }
    }

    @Test
    fun `timing in any schedule or explicit exception transfers the complete series and preserves mixed history`() {
        runBlocking<Unit> {
            val file = versionEight(name = "series.db") { connection ->
                connection.execSQL(sql = "INSERT INTO groups VALUES (7, 'Work', 123)")
                connection.execSQL(
                    sql = """
                    INSERT INTO tasks VALUES
                    (10, 'Mixed history', 20760, 7, 1000, 2000, 1, 'All revisions', NULL, NULL),
                    (20, 'Timed exception', 20760, NULL, 3000, NULL, 1, 'Override', NULL, NULL),
                    (30, 'Untimed series', 20760, NULL, 4000, 5000, 1, 'Leave unchanged', NULL, NULL),
                    (40, 'Base time', 20760, NULL, 6000, NULL, 1, '', 720, 60)
                    """
                        .trimIndent()
                )
                connection.execSQL(
                    sql = """
                    INSERT INTO task_schedules VALUES
                    (1, 10, 20750, 'Weekly', 2, 5, 1, 1, 20759, NULL, 20760, 540, 90),
                    (2, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, 10, NULL, NULL, NULL),
                    (3, 20, 20760, 'Daily', 1, 0, 1, 1, NULL, 5, NULL, NULL, NULL),
                    (4, 30, 20760, 'Monthly', 1, 0, 31, 1, NULL, 6, NULL, NULL, NULL),
                    (5, 40, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, NULL, NULL, NULL)
                    """
                        .trimIndent()
                )
                connection.execSQL(
                    sql = """
                    INSERT INTO task_occurrences VALUES
                    (1, 20750, 10, 20752, 2500, 0, 1, 600, 30),
                    (1, 20751, 10, NULL, NULL, 1, 0, NULL, NULL),
                    (1, 20753, 10, NULL, 2600, 0, 1, NULL, NULL),
                    (2, 20760, 10, 20762, NULL, 0, 0, NULL, NULL),
                    (3, 20761, 20, 20770, 3500, 0, 1, 1430, 120),
                    (3, 20762, 20, NULL, NULL, 1, 0, NULL, NULL),
                    (4, 20760, 30, 20765, 4500, 0, 0, 900, 60)
                    """
                        .trimIndent()
                )
                connection.execSQL(
                    sql = "INSERT INTO events VALUES (10, 'Existing event', 20760, NULL, 100, 1, '', 480, 60)"
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO event_schedules VALUES (1, 10, 20760, 'Daily', 1, 0, 1, 1, " +
                            "NULL, 2, NULL, 480, 60)")
                )
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(
                    expected = listOf(element = 30),
                    actual = database.taskDao().observeTasks().first().map(transform = TaskEntity::id),
                )
                val untimed = assertNotNull(actual = database.taskDao().getRecord(id = 30))
                assertEquals(
                    expected = TaskEntity(
                        id = 30,
                        name = "Untimed series",
                        dueDate = 20760,
                        groupId = null,
                        createdAt = 4000,
                        completedAt = 5000,
                        recurring = true,
                        description = "Leave unchanged",
                    ),
                    actual = untimed.task,
                )
                assertEquals(
                    expected = TaskScheduleEntity(
                        id = 4,
                        taskId = 30,
                        startDate = 20760,
                        frequency = "Monthly",
                        interval = 1,
                        weekdays = 0,
                        dayOfMonth = 31,
                        month = 1,
                        occurrenceCount = 6,
                    ),
                    actual = untimed.schedules.single(),
                )
                // A value in an exception is inert without overridesTiming; it must not convert this series.
                assertEquals(
                    expected = TaskOccurrenceEntity(
                        scheduleId = 4,
                        scheduledDate = 20760,
                        taskId = 30,
                        movedDate = 20765,
                        completedAt = 4500,
                        skipped = false,
                    ),
                    actual = untimed.occurrences.single(),
                )
                val events = database.eventDao().observeRecords().first()
                assertEquals(expected = 4, actual = events.size)
                val mixed = events.single { record -> record.event.name == "Mixed history" }
                assertEquals(expected = 7, actual = mixed.event.groupId)
                assertEquals(expected = 1000L, actual = mixed.event.createdAt)
                assertEquals(expected = "All revisions", actual = mixed.event.description)
                assertNull(actual = mixed.event.startTimeMinutes)
                val timed = mixed.schedules.single { schedule -> schedule.startTimeMinutes != null }
                val noTime = mixed.schedules.single { schedule -> schedule.startTimeMinutes == null }
                assertEquals(
                    expected = EventScheduleEntity(
                        id = timed.id,
                        eventId = mixed.event.id,
                        startDate = 20750,
                        frequency = "Weekly",
                        interval = 2,
                        weekdays = 5,
                        dayOfMonth = 1,
                        month = 1,
                        endDate = 20759,
                        occurrenceCount = null,
                        closedBefore = 20760,
                        startTimeMinutes = 540,
                        durationMinutes = 90,
                    ),
                    actual = timed,
                )
                assertEquals(
                    expected = EventScheduleEntity(
                        id = noTime.id,
                        eventId = mixed.event.id,
                        startDate = 20760,
                        frequency = "Daily",
                        interval = 1,
                        weekdays = 0,
                        dayOfMonth = 1,
                        month = 1,
                        occurrenceCount = 10,
                    ),
                    actual = noTime,
                )
                assertEquals(
                    expected = setOf(
                        EventOccurrenceEntity(
                            scheduleId = timed.id,
                            scheduledDate = 20750,
                            eventId = mixed.event.id,
                            movedDate = 20752,
                            skipped = false,
                            overridesTiming = true,
                            startTimeMinutes = 600,
                            durationMinutes = 30,
                        ),
                        EventOccurrenceEntity(
                            scheduleId = timed.id,
                            scheduledDate = 20751,
                            eventId = mixed.event.id,
                            skipped = true,
                        ),
                        EventOccurrenceEntity(
                            scheduleId = timed.id,
                            scheduledDate = 20753,
                            eventId = mixed.event.id,
                            overridesTiming = true,
                        ),
                        EventOccurrenceEntity(
                            scheduleId = noTime.id,
                            scheduledDate = 20760,
                            eventId = mixed.event.id,
                            movedDate = 20762,
                        ),
                    ),
                    actual = mixed.occurrences.toSet(),
                )
                val exception = events.single { record -> record.event.name == "Timed exception" }
                val schedule = exception.schedules.single()
                assertEquals(expected = 5, actual = schedule.occurrenceCount)
                assertNull(actual = schedule.startTimeMinutes)
                assertEquals(
                    expected = setOf(
                        EventOccurrenceEntity(
                            scheduleId = schedule.id,
                            scheduledDate = 20761,
                            eventId = exception.event.id,
                            movedDate = 20770,
                            skipped = false,
                            overridesTiming = true,
                            startTimeMinutes = 1430,
                            durationMinutes = 120,
                        ),
                        EventOccurrenceEntity(
                            scheduleId = schedule.id,
                            scheduledDate = 20762,
                            eventId = exception.event.id,
                            skipped = true,
                        ),
                    ),
                    actual = exception.occurrences.toSet(),
                )
                assertEquals(
                    expected = 720,
                    actual = events.single { record -> record.event.name == "Base time" }.event.startTimeMinutes,
                )
                assertEquals(expected = 10, actual = database.eventDao().getRecord(id = 10)?.event?.id)
                assertEquals(
                    expected = 10,
                    actual = database.eventDao().getRecord(id = 10)?.schedules?.single()?.eventId,
                )
                assertEquals(
                    expected = 1L,
                    actual = database.queryLong(sql = "SELECT COUNT(*) FROM task_schedules"),
                )
                assertEquals(
                    expected = 1L,
                    actual = database.queryLong(sql = "SELECT COUNT(*) FROM task_occurrences"),
                )
                database.assertForeignKeysValid()
            }
        }
    }

    @Test
    fun `failed conversion rolls back all copies deletions and schema version and can be retried`() {
        runBlocking<Unit> {
            val file = versionEight(name = "rollback.db") { connection ->
                connection.execSQL(
                    sql =
                        ("INSERT INTO tasks VALUES (1, 'First', 20760, NULL, 1000, NULL, 1, '', " +
                            "540, 60), (2, 'Fail migration', 20761, NULL, 2000, NULL, 0, '', 600, 30)")
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO task_schedules VALUES (1, 1, 20760, 'Daily', 1, 0, 1, 1, " +
                            "NULL, 3, NULL, 540, 60)")
                )
                connection.execSQL(
                    sql = "INSERT INTO task_occurrences VALUES (1, 20760, 1, 20762, 1500, 0, 1, 660, 30)"
                )
                connection.execSQL(
                    sql = "INSERT INTO events VALUES (1, 'Existing', 20760, NULL, 100, 0, '', NULL, NULL)"
                )
                connection.execSQL(
                    sql =
                        ("CREATE TRIGGER fail_conversion BEFORE INSERT ON events WHEN NEW.name = " +
                            "'Fail migration' BEGIN SELECT RAISE(ABORT, 'Simulated migration " +
                            "failure'); END")
                )
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertFailsWith<SQLiteException> { database.eventDao().observeRecords().first() }
            }
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                assertEquals(expected = 8L, actual = connection.queryLong(sql = "PRAGMA user_version"))
                assertEquals(expected = 2L, actual = connection.queryLong(sql = "SELECT COUNT(*) FROM tasks"))
                assertEquals(
                    expected = 1L,
                    actual = connection.queryLong(sql = "SELECT COUNT(*) FROM task_schedules"),
                )
                assertEquals(
                    expected = 1L,
                    actual = connection.queryLong(sql = "SELECT COUNT(*) FROM task_occurrences"),
                )
                assertEquals(expected = 1L, actual = connection.queryLong(sql = "SELECT COUNT(*) FROM events"))
                assertEquals(
                    expected = 0L,
                    actual = connection.queryLong(sql = "SELECT COUNT(*) FROM event_schedules"),
                )
                assertEquals(
                    expected = 0L,
                    actual = connection.queryLong(sql = "SELECT COUNT(*) FROM event_occurrences"),
                )
                connection.execSQL(sql = "DROP TRIGGER fail_conversion")
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(expected = 3, actual = database.eventDao().observeRecords().first().size)
                assertTrue(actual = database.taskDao().observeTasks().first().isEmpty())
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
                database.assertForeignKeysValid()
            }
        }
    }

    @Test
    fun `upgrade without timed tasks leaves tasks and events unchanged`() {
        runBlocking<Unit> {
            val file = versionEight(name = "untimed.db") { connection ->
                connection.execSQL(
                    sql =
                        "INSERT INTO tasks VALUES (1, 'Task', 20760, NULL, 1000, 2000, 0, 'Untimed', NULL, NULL)"
                )
                connection.execSQL(
                    sql = "INSERT INTO events VALUES (1, 'Event', 20760, NULL, 3000, 0, 'Existing', 540, 60)"
                )
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(
                    expected = TaskEntity(
                        id = 1,
                        name = "Task",
                        dueDate = 20760,
                        groupId = null,
                        createdAt = 1000,
                        completedAt = 2000,
                        recurring = false,
                        description = "Untimed",
                    ),
                    actual = database.taskDao().getById(id = 1),
                )
                assertEquals(
                    expected = EventEntity(
                        id = 1,
                        name = "Event",
                        dueDate = 20760,
                        groupId = null,
                        createdAt = 3000,
                        recurring = false,
                        description = "Existing",
                        startTimeMinutes = 540,
                        durationMinutes = 60,
                    ),
                    actual = database.eventDao().observeRecords().first().single().event,
                )
                database.assertForeignKeysValid()
            }
        }
    }

    private fun versionEight(name: String, seed: (SQLiteConnection) -> Unit): Path {
        val file = directory.root.toPath().resolve(name)
        val schema = checkNotNull(value = javaClass.getResource("/database/schema-8.sql")).readText()
        BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
            connection.execSQL(sql = "PRAGMA foreign_keys = ON")
            schema
                .lineSequence()
                .filter { line -> line.isNotBlank() && !line.startsWith(prefix = "--") }
                .forEach(action = connection::execSQL)
            seed(connection)
        }
        return file
    }

    private fun SQLiteConnection.queryLong(sql: String): Long {
        return prepare(sql = sql).use { statement ->
            check(value = statement.step())
            statement.getLong(index = 0)
        }
    }

    private suspend fun AppDatabase.queryLong(sql: String): Long {
        return useReaderConnection { connection ->
            connection.usePrepared(sql = sql) { statement ->
                check(value = statement.step())
                statement.getLong(index = 0)
            }
        }
    }

    private suspend fun AppDatabase.assertForeignKeysValid() {
        useReaderConnection { connection ->
            connection.usePrepared(sql = "PRAGMA foreign_key_check") { statement ->
                assertFalse(actual = statement.step())
            }
        }
    }
}
