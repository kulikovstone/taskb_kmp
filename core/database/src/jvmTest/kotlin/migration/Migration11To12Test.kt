package com.mkulikov.taskb.core.database.migration

import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.database.useDatabase
import com.mkulikov.taskb.feature.holiday.api.database.HolidayEntity
import com.mkulikov.taskb.feature.holiday.api.database.HolidayMonthEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class Migration11To12Test {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `existing data survives adding the holiday cache and task calendar visibility`() {
        runBlocking {
            val file = directory.root.toPath().resolve("existing-calendar.db")
            val schema = checkNotNull(value = javaClass.getResource("/database/schema-11.sql")).readText()
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                schema.lineSequence().filter(predicate = String::isNotBlank).forEach(action = connection::execSQL)
                connection.execSQL(
                    sql =
                        ("INSERT INTO application_settings VALUES ('welcomeCompleted', 'true'), " +
                            "('weatherLocation', 'legacy-city')")
                )
                connection.execSQL(sql = "INSERT INTO `groups` (id, name, color) VALUES (1, 'Work', 123)")
                connection.execSQL(
                    sql =
                        ("INSERT INTO tasks (id, name, dueDate, groupId, createdAt, completedAt, " +
                            "recurring, description) VALUES (1, 'Keep this task', 20760, 1, 1000, NULL, 0, " +
                            "'Existing details')")
                )
                connection.execSQL(
                    sql = """
                        INSERT INTO tasks (id, name, dueDate, createdAt, recurring) VALUES
                            (2, 'Every day', 20760, 1000, 1),
                            (3, 'Every other day', 20760, 1000, 1),
                            (4, 'Changed to weekly', 20760, 1000, 1),
                            (5, 'Changed to one-off', 20760, 1000, 0),
                            (6, 'Ended daily series', 20760, 1000, 1)
                    """
                        .trimIndent(),
                )
                connection.execSQL(
                    sql = """
                        INSERT INTO task_schedules (
                            taskId, startDate, frequency, interval, weekdays, dayOfMonth, month, closedBefore
                        ) VALUES
                            (2, 20760, 'Daily', 1, 0, 1, 1, NULL),
                            (3, 20760, 'Daily', 2, 0, 1, 1, NULL),
                            (4, 20760, 'Daily', 1, 0, 1, 1, 20762),
                            (4, 20762, 'Weekly', 1, 1, 1, 1, NULL),
                            (5, 20760, 'Daily', 1, 0, 1, 1, 20762),
                            (6, 20760, 'Daily', 1, 0, 1, 1, 20762)
                    """
                        .trimIndent(),
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO events (id, name, dueDate, groupId, createdAt, recurring, " +
                            "description, startTimeMinutes, durationMinutes, durationDays) VALUES (1, 'Keep " +
                            "this event', 20760, 1, 2000, 0, 'Existing event details', 540, 60, 1)")
                )
            }
            repeat(times = 2) {
                AppDatabase.open(file = file).useDatabase { database ->
                    assertEquals(expected = "true", actual = database.settingsDao().getValue(key = "welcomeCompleted"))
                    assertEquals(
                        expected = "legacy-city",
                        actual = database.settingsDao().getValue(key = "weatherLocation"),
                    )
                    val task = assertNotNull(actual = database.taskDao().getById(id = 1))
                    assertEquals(expected = "Keep this task", actual = task.name)
                    assertEquals(expected = "Existing details", actual = task.description)
                    assertEquals(expected = 1000L, actual = task.createdAt)
                    assertEquals(
                        expected = mapOf(1 to true, 2 to false, 3 to true, 4 to true, 5 to true, 6 to false),
                        actual = database.taskDao().observeTasks().first()
                            .associate { task -> task.id to task.showInCalendar },
                    )
                    assertTrue(
                        actual = database.taskDao().observeTasks().first().none { task -> task.quickDeleteOverdue },
                    )
                    val event = assertNotNull(actual = database.eventDao().getById(id = 1))
                    assertEquals(expected = "Keep this event", actual = event.name)
                    assertEquals(expected = 540, actual = event.startTimeMinutes)
                    assertEquals(expected = 60, actual = event.durationMinutes)
                    assertEquals(expected = 1, actual = event.durationDays)
                    assertNull(actual = database.holidayDao().getMonth(locationKey = "RU", year = 2026, month = 11))
                    assertTrue(
                        actual = database
                            .holidayDao()
                            .getHolidays(locationKey = "RU", fromDate = 0, throughDate = Long.MAX_VALUE)
                            .isEmpty()
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

    @Test
    fun `holidays and empty fetched months persist independently for each location`() {
        runBlocking {
            val file = directory.root.toPath().resolve("cached-holidays.db")
            val date = LocalDate(year = 2026, month = 11, day = 4).toEpochDays()
            val holiday = HolidayEntity(
                locationKey = "RU",
                year = 2026,
                month = 11,
                id = "unity-day",
                date = date,
                name = "Unity Day",
                countryCode = "RU",
            )
            AppDatabase.open(file = file).useDatabase { database ->
                database
                    .holidayDao()
                    .replaceMonth(
                        month = HolidayMonthEntity(locationKey = "RU", year = 2026, month = 11, fetchedAt = 1000),
                        holidays = listOf(element = holiday),
                    )
                database
                    .holidayDao()
                    .replaceMonth(
                        month = HolidayMonthEntity(locationKey = "RU", year = 2026, month = 2, fetchedAt = 2000),
                        holidays = emptyList(),
                    )
                database
                    .holidayDao()
                    .replaceMonth(
                        month = HolidayMonthEntity(locationKey = "US:ca", year = 2026, month = 11, fetchedAt = 3000),
                        holidays = emptyList(),
                    )
            }
            AppDatabase.open(file = file).useDatabase { database ->
                val dao = database.holidayDao()
                assertEquals(
                    expected = 1000L,
                    actual = dao.getMonth(locationKey = "RU", year = 2026, month = 11)?.fetchedAt,
                )
                assertNotNull(actual = dao.getMonth(locationKey = "RU", year = 2026, month = 2))
                assertEquals(
                    expected = listOf(element = holiday),
                    actual = dao.observeHolidays(locationKey = "RU", fromDate = date, throughDate = date).first(),
                )
                assertTrue(
                    actual = dao.getHolidays(locationKey = "US:ca", fromDate = date, throughDate = date).isEmpty()
                )
                assertNull(actual = dao.getMonth(locationKey = "US:ny", year = 2026, month = 11))
                assertNull(actual = dao.getMonth(locationKey = "RU", year = 2027, month = 11))
            }
        }
    }
}
