package com.mkulikov.taskb.di

import androidx.compose.runtime.RememberObserver
import com.mkulikov.taskb.core.database.AppDatabase
import org.koin.dsl.koinApplication

internal class AppDependencyGraph(databaseFactory: () -> AppDatabase) : RememberObserver, AutoCloseable {
    val application = koinApplication { modules(modules = appModule(databaseFactory = databaseFactory)) }
    private var closed = false

    override fun onRemembered() = Unit

    override fun onForgotten() = close()

    override fun onAbandoned() = close()

    override fun close() {
        if (closed) return
        closed = true
        application.close()
    }
}
