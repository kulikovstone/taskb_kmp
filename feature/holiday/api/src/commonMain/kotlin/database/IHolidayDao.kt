package com.mkulikov.taskb.feature.holiday.api.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface IHolidayDao {
    @Query("SELECT * FROM holiday_months WHERE locationKey = :locationKey AND year = :year AND month = :month LIMIT 1")
    suspend fun getMonth(locationKey: String, year: Int, month: Int): HolidayMonthEntity?

    @Query("SELECT * FROM holidays WHERE locationKey = :locationKey AND date >= :fromDate AND date <= :throughDate ORDER BY date, name, id")
    suspend fun getHolidays(locationKey: String, fromDate: Long, throughDate: Long): List<HolidayEntity>

    @Query("SELECT * FROM holidays WHERE locationKey = :locationKey AND date >= :fromDate AND date <= :throughDate ORDER BY date, name, id")
    fun observeHolidays(locationKey: String, fromDate: Long, throughDate: Long): Flow<List<HolidayEntity>>

    @Upsert
    suspend fun saveMonth(month: HolidayMonthEntity)

    @Query("DELETE FROM holidays WHERE locationKey = :locationKey AND year = :year AND month = :month")
    suspend fun deleteMonthHolidays(locationKey: String, year: Int, month: Int)

    @Insert
    suspend fun insertHolidays(holidays: List<HolidayEntity>)

    @Transaction
    suspend fun replaceMonth(month: HolidayMonthEntity, holidays: List<HolidayEntity>) {
        require(holidays.all { it.locationKey == month.locationKey && it.year == month.year && it.month == month.month })
        saveMonth(month)
        deleteMonthHolidays(month.locationKey, month.year, month.month)
        insertHolidays(holidays)
    }
}
