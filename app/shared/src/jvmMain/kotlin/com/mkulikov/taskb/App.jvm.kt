package com.mkulikov.taskb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open

@Composable
actual fun App() {
    App(databaseFactory = remember { { AppDatabase.open() } })
}
