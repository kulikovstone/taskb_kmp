package com.mkulikov.taskb

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.InputStream
import org.jetbrains.skia.Image

fun main() {
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
        Window(
            onCloseRequest = ::exitApplication,
            title = "TaskB",
            icon = appIcon,
            state = rememberWindowState(width = 1900.dp, height = 1180.dp),
        ) {
            App()
        }
    }
}
