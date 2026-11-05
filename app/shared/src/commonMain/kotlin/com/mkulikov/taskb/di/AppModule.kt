package com.mkulikov.taskb.di

import com.mkulikov.taskb.AppVersion
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.di.databaseModule
import com.mkulikov.taskb.core.navigation.di.navigationModule
import com.mkulikov.taskb.feature.calendar.di.calendarModule
import com.mkulikov.taskb.feature.event.di.eventModule
import com.mkulikov.taskb.feature.group.di.groupModule
import com.mkulikov.taskb.feature.settings.di.settingsModule
import com.mkulikov.taskb.feature.hello.di.helloModule
import com.mkulikov.taskb.feature.home.di.homeModule
import com.mkulikov.taskb.feature.task.di.taskModule
import org.koin.dsl.module

internal fun appModule(databaseFactory: () -> AppDatabase) = module {
    includes(
        databaseModule(databaseFactory),
        settingsModule(),
        navigationModule(),
        calendarModule(),
        eventModule(),
        helloModule(),
        homeModule(AppVersion.value),
        groupModule(),
        taskModule(),
    )
}
