package com.mkulikov.taskb

import androidx.compose.runtime.*
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.pollSystemTheme
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.v2.Window
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.rememberWindowState
import com.mkulikov.taskb.window.WindowPreferences
import com.mkulikov.taskb.window.restoreWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.InputStream

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Compose Desktop disables live system theme updates on Linux by default.
    ComposeUiFlags.pollSystemTheme = true
    application {
        val appIcon = remember {
            val bytes = checkNotNull(
                value = Thread.currentThread().contextClassLoader.getResourceAsStream("icons/taskb.png"),
            ) {
                "Application icon resource not found: icons/taskb.png"
            }
                .use(block = InputStream::readBytes)
            BitmapPainter(image = Image.makeFromEncoded(bytes = bytes).toComposeImageBitmap())
        }
        val preferences = remember { WindowPreferences() }
        val initial = remember { restoreWindowState(saved = preferences.load()) }
        val state = rememberWindowState(
            initialScreenProvider = {
                screens.firstOrNull { screen -> screen.id == initial.screenId } ?: defaultScreen
            },
            initialPlacement = if (initial.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            initialBoundsProvider = WindowBoundsProvider.Absolute(bounds = initial.composeBounds),
        )
        if (initial.maximized) {
            LaunchedEffect(key1 = state) {
                val placement = snapshotFlow {
                    if (state.isInitialized && !state.isMinimized) state.placement else null
                }
                placement.first { current -> current == WindowPlacement.Maximized }
                placement.first { current -> current == WindowPlacement.Floating }
                // Some window managers forget the normal bounds when a window was created maximized.
                val restored = restoreWindowState(saved = initial.copy(screenId = state.screenId))
                if (state.bounds != restored.composeBounds) state.requestBounds(bounds = restored.composeBounds)
            }
        }
        var latest by remember { mutableStateOf(value = initial) }
        LaunchedEffect(key1 = state) {
            snapshotFlow { latest.updated(window = state) }.collectLatest { updated ->
                latest = updated
                delay(timeMillis = 400)
                preferences.save(state = updated)
                withContext(context = Dispatchers.IO) { preferences.flush() }
            }
        }
        Window(
            onCloseRequest = {
                preferences.save(state = latest.updated(window = state))
                preferences.flush()
                exitApplication()
            },
            title = "TaskB",
            icon = appIcon,
            state = state,
        ) {
            App()
        }
    }
}
