package com.mkulikov.taskb.core.database.migration

import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.database.useDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*

class Migration10To11Test {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `existing dates times recurrence and overrides survive the new all day span column`() = runBlocking<Unit> {
        val file = directory.root.toPath().resolve("existing-events.db")
        val schema = checkNotNull(javaClass.getResource("/database/schema-10.sql")).readText()
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            schema.lineSequence().filter { it.isNotBlank() && !it.startsWith("--") }.forEach(connection::execSQL)
            connection.execSQL("INSERT INTO events VALUES (1, 'Timed series', 20760, NULL, 1000, 1, 'Keep details', 540, 90), (2, 'Day off', 20761, NULL, 2000, 0, '', NULL, NULL), (3, 'Legacy no date', NULL, NULL, 3000, 0, '', NULL, NULL)")
            connection.execSQL("INSERT INTO event_schedules VALUES (1, 1, 20760, 'Daily', 2, 0, 1, 1, 20790, 5, NULL, 540, 90)")
            connection.execSQL("INSERT INTO event_occurrences VALUES (1, 20760, 1, 20762, 0, 1, 600, 30), (1, 20764, 1, NULL, 1, 0, NULL, NULL)")
        }
        repeat(2) {
            AppDatabase.open(file).useDatabase { database ->
                val records = database.eventDao().observeRecords().first()
                assertEquals(3, records.size)
                val timed = records.single { record -> record.event.id == 1 }
                assertEquals(540, timed.event.startTimeMinutes)
                assertEquals(90, timed.event.durationMinutes)
                assertEquals("Keep details", timed.event.description)
                assertEquals(1000L, timed.event.createdAt)
                assertEquals(1, timed.event.durationDays)
                assertEquals(20790L, timed.schedules.single().endDate)
                assertEquals(5, timed.schedules.single().occurrenceCount)
                assertEquals(1, timed.schedules.single().durationDays)
                assertEquals(600, timed.occurrences.single { occurrence -> occurrence.overridesTiming }.startTimeMinutes)
                assertEquals(30, timed.occurrences.single { occurrence -> occurrence.overridesTiming }.durationMinutes)
                assertTrue(timed.occurrences.all { occurrence -> occurrence.durationDays == 1 })
                assertTrue(timed.occurrences.any { occurrence -> occurrence.skipped })
                assertNull(records.single { record -> record.event.id == 3 }.event.dueDate)
                assertEquals(1, records.single { record -> record.event.id == 2 }.event.durationDays)
                database.useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA user_version") { statement -> assertTrue(statement.step()); assertEquals(12L, statement.getLong(0)) }
                    connection.usePrepared("PRAGMA foreign_key_check") { statement -> assertFalse(statement.step()) }
                }
            }
        }
    }
}
