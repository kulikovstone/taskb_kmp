package com.mkulikov.taskb.feature.task.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.feature.task.navigation.TaskNavEntry
import com.mkulikov.taskb.feature.task.repository.TaskRepository
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun taskModule(): Module = module {
    single<ITaskRepository> { TaskRepository(database = lazy { get<AppDatabase>() }) }
    single<INavEntry>(qualifier = named<TaskNavEntry>()) { TaskNavEntry() }
}
