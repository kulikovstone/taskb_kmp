package com.mkulikov.taskb.feature.event.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.event.navigation.EventNavEntry
import com.mkulikov.taskb.feature.event.repository.EventRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun eventModule() = module {
    single<IEventRepository> { EventRepository(lazy { get<AppDatabase>() }) }
    single<INavEntry>(named<EventNavEntry>()) { EventNavEntry() }
}
