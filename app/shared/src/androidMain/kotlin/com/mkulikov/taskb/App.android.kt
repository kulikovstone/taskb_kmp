package com.mkulikov.taskb

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
actual fun App() {
    val context = LocalContext.current.applicationContext
    App(databaseFactory = remember(key1 = context) { { AppDatabase.open(context = context) } }) { darkTheme ->
        val activity = LocalActivity.current as? ComponentActivity
        val fallbackScrim = TaskBTheme.colors.inverseSurface.toArgb()
        LaunchedEffect(key1 = activity, key2 = darkTheme, key3 = LocalConfiguration.current) {
            val style = if (darkTheme) {
                SystemBarStyle.dark(scrim = Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(scrim = Color.TRANSPARENT, darkScrim = fallbackScrim)
            }
            activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
    }
}
