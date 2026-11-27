package com.mkulikov.taskb.feature.hello.di

import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.hello.navigation.HelloNavEntry
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun helloModule(): Module = module {
    single<INavEntry>(qualifier = named<HelloNavEntry>()) { HelloNavEntry(settings = get()) }
}
