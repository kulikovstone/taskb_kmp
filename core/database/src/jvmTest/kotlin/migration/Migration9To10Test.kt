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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.test.*

class Migration9To10Test {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `version nine timed tasks are transferred before timing columns are removed and date-only history survives`() = runBlocking<Unit> {
        val file = versionNine("remove-timing.db") { connection ->
            connection.execSQL("INSERT INTO groups VALUES (7, 'Work', 123)")
            connection.execSQL("""
                INSERT INTO tasks VALUES
                (1, 'Created after version 9', 20760, 7, 1000, 2000, 0, 'Timed', 570, 90),
                (2, 'Completed task', 20760, 7, 3000, 4000, 0, 'Keep completion', NULL, NULL),
                (3, 'Date-only series', 20760, NULL, 5000, NULL, 1, 'Keep schedule', NULL, NULL),
                (4, 'Timed series', 20760, NULL, 6000, NULL, 1, 'Transfer schedule', NULL, NULL)
            """.trimIndent())
            connection.execSQL("INSERT INTO task_schedules VALUES (1, 3, 20760, 'Daily', 2, 0, 1, 1, NULL, 5, NULL, NULL, NULL), (2, 4, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, NULL, 720, 30)")
            connection.execSQL("INSERT INTO task_occurrences VALUES (1, 20760, 3, 20761, 5500, 0, 0, NULL, NULL), (2, 20760, 4, 20762, NULL, 0, 1, 780, 60)")
            connection.execSQL("INSERT INTO events VALUES (1, 'Existing event', 20760, NULL, 100, 0, 'Keep timing', 540, 60)")
        }
        repeat(2) {
            AppDatabase.open(file).useDatabase { database ->
                assertEquals(listOf(2, 3), database.taskDao().observeTasks().first().map { task -> task.id })
                assertEquals(TaskEntity(2, "Completed task", 20760, 7, 3000, 4000, false, "Keep completion"), database.taskDao().getById(2))
                val series = assertNotNull(database.taskDao().getRecord(3))
                assertEquals(2, series.schedules.single().interval)
                assertEquals(5, series.schedules.single().occurrenceCount)
                assertEquals(20761L, series.occurrences.single().movedDate)
                assertEquals(5500L, series.occurrences.single().completedAt)
                val events = database.eventDao().observeRecords().first()
                assertEquals(3, events.size)
                val moved = events.single { event -> event.event.name == "Created after version 9" }.event
                assertEquals("Timed", moved.description)
                assertEquals(7, moved.groupId)
                assertEquals(1000L, moved.createdAt)
                assertEquals(570, moved.startTimeMinutes)
                assertEquals(90, moved.durationMinutes)
                val timedSeries = events.single { event -> event.event.name == "Timed series" }
                assertEquals(720, timedSeries.schedules.single().startTimeMinutes)
                assertEquals(780, timedSeries.occurrences.single().startTimeMinutes)
                assertEquals(60, timedSeries.occurrences.single().durationMinutes)
                assertEquals(540, events.single { event -> event.event.id == 1 }.event.startTimeMinutes)
                database.useReaderConnection { connection ->
                    for (table in listOf("tasks", "task_schedules", "task_occurrences")) {
                        val columns = connection.usePrepared("PRAGMA table_info($table)") { statement ->
                            buildSet { while (statement.step()) add(statement.getText(1)) }
                        }
                        assertTrue(columns.intersect(setOf("startTimeMinutes", "durationMinutes", "overridesTiming")).isEmpty(), table)
                    }
                    connection.usePrepared("PRAGMA foreign_key_check") { statement -> assertFalse(statement.step()) }
                }
            }
        }
    }

    @Test
    fun `failed column removal rolls back the preceding event transfer and preserves version nine data`() = runBlocking<Unit> {
        val file = versionNine("rollback-columns.db") { connection ->
            connection.execSQL("INSERT INTO tasks VALUES (1, 'Timed task', 20760, NULL, 1000, NULL, 0, '', 540, 60)")
            connection.execSQL("CREATE INDEX block_column_removal ON task_occurrences (overridesTiming)")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertFailsWith<SQLiteException> { database.taskDao().observeTasks().first() }
        }
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            fun count(sql: String) = connection.prepare(sql).use { statement -> check(statement.step()); statement.getLong(0) }
            assertEquals(9L, count("PRAGMA user_version"))
            assertEquals(1L, count("SELECT COUNT(*) FROM tasks"))
            assertEquals(540L, count("SELECT startTimeMinutes FROM tasks WHERE id = 1"))
            assertEquals(0L, count("SELECT COUNT(*) FROM events"))
            connection.execSQL("DROP INDEX block_column_removal")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertTrue(database.taskDao().observeTasks().first().isEmpty())
            assertEquals("Timed task", database.eventDao().observeRecords().first().single().event.name)
        }
    }

    private fun versionNine(name: String, seed: (SQLiteConnection) -> Unit): Path {
        val file = directory.root.toPath().resolve(name)
        // Versions 8 and 9 have identical tables; version 9 adds only a data migration.
        val schema = checkNotNull(javaClass.getResource("/database/schema-8.sql")).readText()
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("PRAGMA foreign_keys = ON")
            schema.lineSequence().filter { it.isNotBlank() && !it.startsWith("--") }.forEach(connection::execSQL)
            connection.execSQL("PRAGMA user_version = 9")
            seed(connection)
        }
        return file
    }
}
