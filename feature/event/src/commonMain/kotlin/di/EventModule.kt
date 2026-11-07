package com.mkulikov.taskb.feature.event.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.event.navigation.EventNavEntry
import com.mkulikov.taskb.feature.event.repository.EventRepository
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun eventModule(): Module = module {
    single<IEventRepository> { EventRepository(database = lazy { get<AppDatabase>() }) }
    single<INavEntry>(qualifier = named<EventNavEntry>()) { EventNavEntry() }
}
