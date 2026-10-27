package com.mkulikov.taskb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open

@Composable
actual fun App() {
    val context = LocalContext.current.applicationContext
    App(databaseFactory = remember(context) { { AppDatabase.open(context) } })
}
