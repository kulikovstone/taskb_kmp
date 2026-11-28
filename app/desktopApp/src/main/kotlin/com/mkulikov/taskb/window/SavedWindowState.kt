package com.mkulikov.taskb.window

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.v2.WindowState
import androidx.compose.ui.window.v2.awtBounds
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.geom.Area

internal const val DEFAULT_WINDOW_WIDTH = 1640
internal const val DEFAULT_WINDOW_HEIGHT = 1120

internal data class SavedWindowState(val screenId: String, val floatingBounds: Rectangle, val maximized: Boolean) {
    val composeBounds: DpRect
        get() = DpRect(
            left = floatingBounds.x.dp,
            top = floatingBounds.y.dp,
            right = (floatingBounds.x + floatingBounds.width).dp,
            bottom = (floatingBounds.y + floatingBounds.height).dp,
        )

    @OptIn(ExperimentalComposeUiApi::class)
    fun updated(window: WindowState): SavedWindowState {
        if (!window.isInitialized || window.isMinimized) return this
        return when (window.placement) {
            WindowPlacement.Floating -> copy(
                screenId = window.screenId,
                floatingBounds = window.awtBounds,
                maximized = false,
            )
            WindowPlacement.Maximized -> copy(screenId = window.screenId, maximized = true)
            WindowPlacement.Fullscreen -> this
        }
    }
}

internal fun restoreWindowState(saved: SavedWindowState?): SavedWindowState {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val screens = environment.screenDevices.associate { device ->
        val configuration = device.defaultConfiguration
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        device.iDstring to Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
    }
    return restoreWindowState(
        saved = saved,
        screens = screens,
        defaultScreenId = environment.defaultScreenDevice.iDstring,
    )
}

internal fun restoreWindowState(
    saved: SavedWindowState?,
    screens: Map<String, Rectangle>,
    defaultScreenId: String,
): SavedWindowState {
    val targetId = saved?.screenId?.takeIf(predicate = screens::containsKey) ?: defaultScreenId
    val target = screens.getValue(key = targetId)
    val bounds = saved?.floatingBounds
    val onTarget = saved != null && targetId == saved.screenId && target.intersects(bounds)
    if (onTarget) {
        // Preserve a window spanning multiple screens if its entire area is still visible.
        val outside = Area(bounds)
        screens.values.forEach { screen -> outside.subtract(Area(screen)) }
        if (outside.isEmpty) return saved
    }

    val desiredWidth = bounds?.width ?: DEFAULT_WINDOW_WIDTH
    val desiredHeight = bounds?.height ?: DEFAULT_WINDOW_HEIGHT
    // Some window managers classify a new window covering the whole monitor as fullscreen.
    val margin = if (desiredWidth >= target.width && desiredHeight >= target.height) {
        minOf(a = 16, b = (target.width - 1) / 2, c = (target.height - 1) / 2)
    } else {
        0
    }
    val width = desiredWidth.coerceAtMost(maximumValue = target.width - 2 * margin)
    val height = desiredHeight.coerceAtMost(maximumValue = target.height - 2 * margin)
    val desiredX = if (onTarget) saved.floatingBounds.x else target.x + (target.width - width) / 2
    val desiredY = if (onTarget) saved.floatingBounds.y else target.y + (target.height - height) / 2
    val x = desiredX.coerceIn(minimumValue = target.x, maximumValue = target.x + target.width - width)
    val y = desiredY.coerceIn(minimumValue = target.y, maximumValue = target.y + target.height - height)
    return SavedWindowState(
        screenId = targetId,
        floatingBounds = Rectangle(x, y, width, height),
        maximized = saved?.maximized == true,
    )
}
