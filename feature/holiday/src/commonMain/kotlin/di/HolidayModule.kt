package com.mkulikov.taskb.feature.holiday.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.holiday.HolidayRepository
import com.mkulikov.taskb.feature.holiday.api.database.IHolidayDao
import com.mkulikov.taskb.feature.holiday.api.repository.IHolidayRepository
import com.mkulikov.taskb.feature.holiday.network.EnricoApi
import com.mkulikov.taskb.feature.holiday.network.HolidayClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

fun holidayModule(): Module = module {
    single<IHolidayDao> { get<AppDatabase>().holidayDao() }
    single { HolidayClient() } onClose { client -> client?.close() }
    single { EnricoApi(client = get<HolidayClient>().client) }
    single<IHolidayRepository> { HolidayRepository(api = get(), locations = get(), dao = inject()) }
}
