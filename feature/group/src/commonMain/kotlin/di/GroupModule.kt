package com.mkulikov.taskb.feature.group.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.group.navigation.GroupNavEntry
import com.mkulikov.taskb.feature.group.repository.GroupRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun groupModule() = module {
    single<IGroupRepository> { GroupRepository(lazy { get<AppDatabase>().groupDao() }) }
    single<INavEntry>(named<GroupNavEntry>()) { GroupNavEntry() }
}
