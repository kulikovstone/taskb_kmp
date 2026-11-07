package com.mkulikov.taskb.core.database

import androidx.sqlite.SQLiteException
import com.mkulikov.taskb.feature.holiday.api.database.HolidayEntity
import com.mkulikov.taskb.feature.holiday.api.database.HolidayMonthEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class HolidayDaoTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `failed month replacement rolls back both the snapshot and existing holidays`() {
        runBlocking<Unit> {
            val file = directory.root.toPath().resolve("holiday-transactions.db")
            AppDatabase.open(file = file).useDatabase { database ->
                val dao = database.holidayDao()
                val date = LocalDate(year = 2026, month = 11, day = 4).toEpochDays()
                val original = HolidayEntity(
                    locationKey = "RU",
                    year = 2026,
                    month = 11,
                    id = "unity-day",
                    date = date,
                    name = "Unity Day",
                    countryCode = "RU",
                )
                dao.replaceMonth(
                    month = HolidayMonthEntity(locationKey = "RU", year = 2026, month = 11, fetchedAt = 1000),
                    holidays = listOf(element = original),
                )
                val duplicate = original.copy(name = "Must not be saved")
                assertFailsWith<SQLiteException> {
                    dao.replaceMonth(
                        month = HolidayMonthEntity(locationKey = "RU", year = 2026, month = 11, fetchedAt = 2000),
                        holidays = listOf(duplicate, duplicate),
                    )
                }
                assertEquals(
                    expected = 1000L,
                    actual = dao.getMonth(locationKey = "RU", year = 2026, month = 11)?.fetchedAt,
                )
                assertEquals(
                    expected = listOf(element = original),
                    actual = dao.getHolidays(locationKey = "RU", fromDate = date, throughDate = date),
                )
            }
        }
    }
}
