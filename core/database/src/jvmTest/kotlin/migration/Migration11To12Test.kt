package com.mkulikov.taskb.core.database.migration

import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.database.useDatabase
import com.mkulikov.taskb.feature.holiday.api.database.HolidayEntity
import com.mkulikov.taskb.feature.holiday.api.database.HolidayMonthEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Migration11To12Test {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `existing tasks settings and events survive adding the holiday cache`() = runBlocking {
        val file = directory.root.toPath().resolve("existing-calendar.db")
        val schema = checkNotNull(javaClass.getResource("/database/schema-11.sql")).readText()
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            schema.lineSequence().filter(String::isNotBlank).forEach(connection::execSQL)
            connection.execSQL("INSERT INTO application_settings VALUES ('welcomeCompleted', 'true'), ('weatherLocation', 'legacy-city')")
            connection.execSQL("INSERT INTO `groups` (id, name, color) VALUES (1, 'Work', 123)")
            connection.execSQL("INSERT INTO tasks (id, name, dueDate, groupId, createdAt, completedAt, recurring, description) VALUES (1, 'Keep this task', 20760, 1, 1000, NULL, 0, 'Existing details')")
            connection.execSQL("INSERT INTO events (id, name, dueDate, groupId, createdAt, recurring, description, startTimeMinutes, durationMinutes, durationDays) VALUES (1, 'Keep this event', 20760, 1, 2000, 0, 'Existing event details', 540, 60, 1)")
        }
        repeat(2) {
            AppDatabase.open(file).useDatabase { database ->
                assertEquals("true", database.settingsDao().getValue("welcomeCompleted"))
                assertEquals("legacy-city", database.settingsDao().getValue("weatherLocation"))
                val task = assertNotNull(database.taskDao().getById(1))
                assertEquals("Keep this task", task.name)
                assertEquals("Existing details", task.description)
                assertEquals(1000L, task.createdAt)
                val event = assertNotNull(database.eventDao().getById(1))
                assertEquals("Keep this event", event.name)
                assertEquals(540, event.startTimeMinutes)
                assertEquals(60, event.durationMinutes)
                assertEquals(1, event.durationDays)
                assertNull(database.holidayDao().getMonth("RU", 2026, 11))
                assertTrue(database.holidayDao().getHolidays("RU", 0, Long.MAX_VALUE).isEmpty())
                database.useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA user_version") { statement ->
                        assertTrue(statement.step())
                        assertEquals(12L, statement.getLong(0))
                    }
                    connection.usePrepared("PRAGMA foreign_key_check") { statement -> assertFalse(statement.step()) }
                }
            }
        }
    }

    @Test
    fun `holidays and empty fetched months persist independently for each location`() = runBlocking {
        val file = directory.root.toPath().resolve("cached-holidays.db")
        val date = LocalDate(2026, 11, 4).toEpochDays()
        val holiday = HolidayEntity("RU", 2026, 11, "unity-day", date, "Unity Day", "RU")
        AppDatabase.open(file).useDatabase { database ->
            database.holidayDao().replaceMonth(HolidayMonthEntity("RU", 2026, 11, 1000), listOf(holiday))
            database.holidayDao().replaceMonth(HolidayMonthEntity("RU", 2026, 2, 2000), emptyList())
            database.holidayDao().replaceMonth(HolidayMonthEntity("US:ca", 2026, 11, 3000), emptyList())
        }
        AppDatabase.open(file).useDatabase { database ->
            val dao = database.holidayDao()
            assertEquals(1000L, dao.getMonth("RU", 2026, 11)?.fetchedAt)
            assertNotNull(dao.getMonth("RU", 2026, 2))
            assertEquals(listOf(holiday), dao.observeHolidays("RU", date, date).first())
            assertTrue(dao.getHolidays("US:ca", date, date).isEmpty())
            assertNull(dao.getMonth("US:ny", 2026, 11))
            assertNull(dao.getMonth("RU", 2027, 11))
        }
    }
}
