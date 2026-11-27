package com.mkulikov.taskb.di

import com.mkulikov.taskb.AppVersion
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.di.databaseModule
import com.mkulikov.taskb.core.navigation.di.navigationModule
import com.mkulikov.taskb.feature.calendar.di.calendarModule
import com.mkulikov.taskb.feature.group.di.groupModule
import com.mkulikov.taskb.feature.hello.di.helloModule
import com.mkulikov.taskb.feature.holiday.di.holidayModule
import com.mkulikov.taskb.feature.home.di.homeModule
import com.mkulikov.taskb.feature.item.di.itemModule
import com.mkulikov.taskb.feature.settings.di.settingsModule
import com.mkulikov.taskb.feature.weather.di.weatherModule
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun appModule(databaseFactory: () -> AppDatabase): Module = module {
    includes(
        databaseModule(databaseFactory = databaseFactory),
        settingsModule(),
        weatherModule(),
        holidayModule(),
        navigationModule(),
        calendarModule(),
        helloModule(),
        homeModule(appVersion = AppVersion.value),
        groupModule(),
        itemModule(),
    )
}
