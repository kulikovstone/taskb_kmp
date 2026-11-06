package com.mkulikov.taskb.core.database

import androidx.sqlite.SQLiteException
import com.mkulikov.taskb.feature.holiday.api.database.HolidayEntity
import com.mkulikov.taskb.feature.holiday.api.database.HolidayMonthEntity
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HolidayDaoTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `failed month replacement rolls back both the snapshot and existing holidays`() = runBlocking<Unit> {
        val file = directory.root.toPath().resolve("holiday-transactions.db")
        AppDatabase.open(file).useDatabase { database ->
            val dao = database.holidayDao()
            val date = LocalDate(2026, 11, 4).toEpochDays()
            val original = HolidayEntity("RU", 2026, 11, "unity-day", date, "Unity Day", "RU")
            dao.replaceMonth(HolidayMonthEntity("RU", 2026, 11, 1000), listOf(original))
            val duplicate = original.copy(name = "Must not be saved")
            assertFailsWith<SQLiteException> {
                dao.replaceMonth(HolidayMonthEntity("RU", 2026, 11, 2000), listOf(duplicate, duplicate))
            }
            assertEquals(1000L, dao.getMonth("RU", 2026, 11)?.fetchedAt)
            assertEquals(listOf(original), dao.getHolidays("RU", date, date))
        }
    }
}
