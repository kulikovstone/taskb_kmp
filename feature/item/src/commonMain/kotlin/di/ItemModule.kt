package com.mkulikov.taskb.feature.item.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.item.api.repository.IEventRepository
import com.mkulikov.taskb.feature.item.api.repository.ITaskRepository
import com.mkulikov.taskb.feature.item.navigation.ItemNavEntry
import com.mkulikov.taskb.feature.item.repository.EventRepository
import com.mkulikov.taskb.feature.item.repository.TaskRepository
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun itemModule(): Module = module {
    single<IEventRepository> { EventRepository(database = lazy { get<AppDatabase>() }) }
    single<ITaskRepository> { TaskRepository(database = lazy { get<AppDatabase>() }) }
    single<INavEntry>(qualifier = named<ItemNavEntry>()) { ItemNavEntry() }
}
