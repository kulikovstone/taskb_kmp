package com.mkulikov.taskb.core.database.migration

import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.database.useDatabase
import com.mkulikov.taskb.feature.event.api.database.EventOccurrenceEntity
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class Migration10To11Test {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `existing dates times recurrence and overrides survive the new all day span column`() {
        runBlocking<Unit> {
            val file = directory.root.toPath().resolve("existing-events.db")
            val schema = checkNotNull(value = javaClass.getResource("/database/schema-10.sql")).readText()
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                schema
                    .lineSequence()
                    .filter { line -> line.isNotBlank() && !line.startsWith(prefix = "--") }
                    .forEach(action = connection::execSQL)
                connection.execSQL(
                    sql =
                        ("INSERT INTO events VALUES (1, 'Timed series', 20760, NULL, 1000, 1, 'Keep " +
                            "details', 540, 90), (2, 'Day off', 20761, NULL, 2000, 0, '', NULL, NULL), " +
                            "(3, 'Legacy no date', NULL, NULL, 3000, 0, '', NULL, NULL)")
                )
                connection.execSQL(
                    sql =
                        "INSERT INTO event_schedules VALUES (1, 1, 20760, 'Daily', 2, 0, 1, 1, 20790, 5, NULL, 540, 90)"
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO event_occurrences VALUES (1, 20760, 1, 20762, 0, 1, 600, 30), " +
                            "(1, 20764, 1, NULL, 1, 0, NULL, NULL)")
                )
            }
            repeat(times = 2) {
                AppDatabase.open(file = file).useDatabase { database ->
                    val records = database.eventDao().observeRecords().first()
                    assertEquals(expected = 3, actual = records.size)
                    val timed = records.single { record -> record.event.id == 1 }
                    assertEquals(expected = 540, actual = timed.event.startTimeMinutes)
                    assertEquals(expected = 90, actual = timed.event.durationMinutes)
                    assertEquals(expected = "Keep details", actual = timed.event.description)
                    assertEquals(expected = 1000L, actual = timed.event.createdAt)
                    assertEquals(expected = 1, actual = timed.event.durationDays)
                    assertEquals(expected = 20790L, actual = timed.schedules.single().endDate)
                    assertEquals(expected = 5, actual = timed.schedules.single().occurrenceCount)
                    assertEquals(expected = 1, actual = timed.schedules.single().durationDays)
                    assertEquals(
                        expected = 600,
                        actual = timed.occurrences.single { occurrence -> occurrence.overridesTiming }.startTimeMinutes,
                    )
                    assertEquals(
                        expected = 30,
                        actual = timed.occurrences.single { occurrence -> occurrence.overridesTiming }.durationMinutes,
                    )
                    assertTrue(actual = timed.occurrences.all { occurrence -> occurrence.durationDays == 1 })
                    assertTrue(actual = timed.occurrences.any(predicate = EventOccurrenceEntity::skipped))
                    assertNull(actual = records.single { record -> record.event.id == 3 }.event.dueDate)
                    assertEquals(
                        expected = 1,
                        actual = records.single { record -> record.event.id == 2 }.event.durationDays,
                    )
                    database.useReaderConnection { connection ->
                        connection.usePrepared(sql = "PRAGMA user_version") { statement ->
                            assertTrue(actual = statement.step())
                            assertEquals(expected = 14L, actual = statement.getLong(index = 0))
                        }
                        connection.usePrepared(sql = "PRAGMA foreign_key_check") { statement ->
                            assertFalse(actual = statement.step())
                        }
                    }
                }
            }
        }
    }
}
