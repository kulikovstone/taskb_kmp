package com.mkulikov.taskb.feature.calendar.di

import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.calendar.navigation.CalendarNavEntry
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun calendarModule() = module {
    single<INavEntry>(named<CalendarNavEntry>()) { CalendarNavEntry() }
}
