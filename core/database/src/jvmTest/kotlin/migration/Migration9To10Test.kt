package com.mkulikov.taskb.core.database.migration

import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.database.useDatabase
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import java.nio.file.Path
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class Migration9To10Test {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `version nine timed tasks are transferred before timing columns are removed and date-only history survives`() {
        runBlocking<Unit> {
            val file = versionNine(name = "remove-timing.db") { connection ->
                connection.execSQL(sql = "INSERT INTO groups VALUES (7, 'Work', 123)")
                connection.execSQL(
                    sql = """
                    INSERT INTO tasks VALUES
                    (1, 'Created after version 9', 20760, 7, 1000, 2000, 0, 'Timed', 570, 90),
                    (2, 'Completed task', 20760, 7, 3000, 4000, 0, 'Keep completion', NULL, NULL),
                    (3, 'Date-only series', 20760, NULL, 5000, NULL, 1, 'Keep schedule', NULL, NULL),
                    (4, 'Timed series', 20760, NULL, 6000, NULL, 1, 'Transfer schedule', NULL, NULL)
                    """
                        .trimIndent()
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO task_schedules VALUES (1, 3, 20760, 'Daily', 2, 0, 1, 1, " +
                            "NULL, 5, NULL, NULL, NULL), (2, 4, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, " +
                            "NULL, 720, 30)")
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO task_occurrences VALUES (1, 20760, 3, 20761, 5500, 0, 0, " +
                            "NULL, NULL), (2, 20760, 4, 20762, NULL, 0, 1, 780, 60)")
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO events VALUES (1, 'Existing event', 20760, NULL, 100, 0, " +
                            "'Keep timing', 540, 60)")
                )
            }
            repeat(times = 2) {
                AppDatabase.open(file = file).useDatabase { database ->
                    assertEquals(
                        expected = listOf(2, 3),
                        actual = database.taskDao().observeTasks().first().map(transform = TaskEntity::id),
                    )
                    assertEquals(
                        expected = TaskEntity(
                            id = 2,
                            name = "Completed task",
                            dueDate = 20760,
                            groupId = 7,
                            createdAt = 3000,
                            completedAt = 4000,
                            recurring = false,
                            description = "Keep completion",
                        ),
                        actual = database.taskDao().getById(id = 2),
                    )
                    val series = assertNotNull(actual = database.taskDao().getRecord(id = 3))
                    assertEquals(expected = 2, actual = series.schedules.single().interval)
                    assertEquals(expected = 5, actual = series.schedules.single().occurrenceCount)
                    assertEquals(expected = 20761L, actual = series.occurrences.single().movedDate)
                    assertEquals(expected = 5500L, actual = series.occurrences.single().completedAt)
                    val events = database.eventDao().observeRecords().first()
                    assertEquals(expected = 3, actual = events.size)
                    val moved = events.single { event -> event.event.name == "Created after version 9" }.event
                    assertEquals(expected = "Timed", actual = moved.description)
                    assertEquals(expected = 7, actual = moved.groupId)
                    assertEquals(expected = 1000L, actual = moved.createdAt)
                    assertEquals(expected = 570, actual = moved.startTimeMinutes)
                    assertEquals(expected = 90, actual = moved.durationMinutes)
                    val timedSeries = events.single { event -> event.event.name == "Timed series" }
                    assertEquals(expected = 720, actual = timedSeries.schedules.single().startTimeMinutes)
                    assertEquals(expected = 780, actual = timedSeries.occurrences.single().startTimeMinutes)
                    assertEquals(expected = 60, actual = timedSeries.occurrences.single().durationMinutes)
                    assertEquals(
                        expected = 540,
                        actual = events.single { event -> event.event.id == 1 }.event.startTimeMinutes,
                    )
                    database.useReaderConnection { connection ->
                        for (table in listOf("tasks", "task_schedules", "task_occurrences")) {
                            val columns = connection.usePrepared(sql = "PRAGMA table_info($table)") { statement ->
                                buildSet {
                                    while (statement.step()) add(element = statement.getText(index = 1))
                                }
                            }
                            assertTrue(
                                actual = columns
                                    .intersect(
                                        other = setOf("startTimeMinutes", "durationMinutes", "overridesTiming")
                                    )
                                    .isEmpty(),
                                message = table,
                            )
                        }
                        connection.usePrepared(sql = "PRAGMA foreign_key_check") { statement ->
                            assertFalse(actual = statement.step())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `failed column removal rolls back the preceding event transfer and preserves version nine data`() {
        runBlocking<Unit> {
            val file = versionNine(name = "rollback-columns.db") { connection ->
                connection.execSQL(
                    sql = "INSERT INTO tasks VALUES (1, 'Timed task', 20760, NULL, 1000, NULL, 0, '', 540, 60)"
                )
                connection.execSQL(
                    sql = "CREATE INDEX block_column_removal ON task_occurrences (overridesTiming)"
                )
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertFailsWith<SQLiteException> { database.taskDao().observeTasks().first() }
            }
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                fun count(sql: String): Long {
                    return connection.prepare(sql = sql).use { statement ->
                        check(value = statement.step())
                        statement.getLong(index = 0)
                    }
                }
                assertEquals(expected = 9L, actual = count(sql = "PRAGMA user_version"))
                assertEquals(expected = 1L, actual = count(sql = "SELECT COUNT(*) FROM tasks"))
                assertEquals(
                    expected = 540L,
                    actual = count(sql = "SELECT startTimeMinutes FROM tasks WHERE id = 1"),
                )
                assertEquals(expected = 0L, actual = count(sql = "SELECT COUNT(*) FROM events"))
                connection.execSQL(sql = "DROP INDEX block_column_removal")
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertTrue(actual = database.taskDao().observeTasks().first().isEmpty())
                assertEquals(
                    expected = "Timed task",
                    actual = database.eventDao().observeRecords().first().single().event.name,
                )
            }
        }
    }

    private fun versionNine(name: String, seed: (SQLiteConnection) -> Unit): Path {
        val file = directory.root.toPath().resolve(name)
        // Versions 8 and 9 have identical tables; version 9 adds only a data migration.
        val schema = checkNotNull(value = javaClass.getResource("/database/schema-8.sql")).readText()
        BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
            connection.execSQL(sql = "PRAGMA foreign_keys = ON")
            schema
                .lineSequence()
                .filter { line -> line.isNotBlank() && !line.startsWith(prefix = "--") }
                .forEach(action = connection::execSQL)
            connection.execSQL(sql = "PRAGMA user_version = 9")
            seed(connection)
        }
        return file
    }
}
