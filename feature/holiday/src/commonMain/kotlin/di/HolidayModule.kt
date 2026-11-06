package com.mkulikov.taskb.feature.holiday.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.holiday.HolidayRepository
import com.mkulikov.taskb.feature.holiday.api.database.IHolidayDao
import com.mkulikov.taskb.feature.holiday.api.repository.IHolidayRepository
import com.mkulikov.taskb.feature.holiday.network.EnricoApi
import com.mkulikov.taskb.feature.holiday.network.HolidayClient
import org.koin.dsl.module
import org.koin.dsl.onClose

fun holidayModule() = module {
    single<IHolidayDao> { get<AppDatabase>().holidayDao() }
    single { HolidayClient() } onClose { it?.close() }
    single { EnricoApi(get<HolidayClient>().client) }
    single<IHolidayRepository> { HolidayRepository(get(), get(), inject()) }
}
