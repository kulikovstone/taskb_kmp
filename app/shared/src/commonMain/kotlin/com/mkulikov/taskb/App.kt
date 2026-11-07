package com.mkulikov.taskb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.di.AppDependencyGraph
import com.mkulikov.taskb.ui.theme.TaskBTheme
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject

@Composable expect fun App()

@Composable
internal fun App(databaseFactory: () -> AppDatabase) {
    val graph = remember(key1 = databaseFactory) { AppDependencyGraph(databaseFactory = databaseFactory) }
    KoinIsolatedContext(context = graph.application) {
        TaskBTheme {
            TaskBApp(settings = koinInject())
        }
    }
}
