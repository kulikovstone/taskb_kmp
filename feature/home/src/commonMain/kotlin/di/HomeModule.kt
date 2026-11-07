package com.mkulikov.taskb.feature.home.di

import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.home.navigation.HomeNavEntry
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun homeModule(appVersion: String): Module = module {
    single<INavEntry>(qualifier = named<HomeNavEntry>()) { HomeNavEntry(appVersion = appVersion) }
}
