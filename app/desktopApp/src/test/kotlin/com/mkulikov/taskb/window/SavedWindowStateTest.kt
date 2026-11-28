package com.mkulikov.taskb.window

import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedWindowStateTest {
    private val primary = Rectangle(0, 30, 1600, 870)
    private val secondary = Rectangle(-1920, 0, 1920, 1080)
    private val both = mapOf("primary" to primary, "secondary" to secondary)

    @Test
    fun restoresExactPositionOnMonitorLeftOfPrimary() {
        val saved = SavedWindowState(
            screenId = "secondary",
            floatingBounds = Rectangle(-1350, 120, 820, 530),
            maximized = false,
        )
        assertEquals(saved, restoreWindowState(saved = saved, screens = both, defaultScreenId = "primary"))
    }

    @Test
    fun keepsWindowSpanningTwoConnectedMonitors() {
        val saved = SavedWindowState(
            screenId = "primary",
            floatingBounds = Rectangle(-300, 80, 1200, 700),
            maximized = false,
        )
        assertEquals(saved, restoreWindowState(saved = saved, screens = both, defaultScreenId = "primary"))
    }

    @Test
    fun reopensMaximizedOnPrimaryWithUsableFloatingBoundsAfterDisconnect() {
        val saved = SavedWindowState(
            screenId = "secondary",
            floatingBounds = Rectangle(-1900, 20, 1800, 620),
            maximized = true,
        )
        val restored = restoreWindowState(
            saved = saved,
            screens = mapOf("primary" to primary),
            defaultScreenId = "primary",
        )
        val expected = SavedWindowState(
            screenId = "primary",
            floatingBounds = Rectangle(0, 155, 1600, 620),
            maximized = true,
        )
        assertEquals(expected, restored)
    }

    @Test
    fun movesPartiallyOffscreenWindowInsideWorkAreaAfterResolutionChange() {
        val saved = SavedWindowState(
            screenId = "primary",
            floatingBounds = Rectangle(1250, -10, 750, 600),
            maximized = false,
        )
        val restored = restoreWindowState(saved = saved, screens = both, defaultScreenId = "primary")
        assertEquals(Rectangle(850, 30, 750, 600), restored.floatingBounds)
    }

    @Test
    fun firstLaunchCentersDefaultWindowInAvailableWorkArea() {
        val workArea = Rectangle(0, 30, DEFAULT_WINDOW_WIDTH + 280, DEFAULT_WINDOW_HEIGHT + 50)
        val restored = restoreWindowState(
            saved = null,
            screens = mapOf("primary" to workArea),
            defaultScreenId = "primary",
        )
        assertEquals(Rectangle(140, 55, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT), restored.floatingBounds)
    }

    @Test
    fun firstLaunchOnSmallScreenLeavesRoomForWindowBorders() {
        val restored = restoreWindowState(saved = null, screens = both, defaultScreenId = "primary")
        val expected = SavedWindowState(
            screenId = "primary",
            floatingBounds = Rectangle(16, 46, 1568, 838),
            maximized = false,
        )
        assertEquals(expected, restored)
    }
}
