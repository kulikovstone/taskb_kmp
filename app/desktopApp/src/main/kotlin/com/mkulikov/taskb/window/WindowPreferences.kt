package com.mkulikov.taskb.window

import java.awt.Rectangle
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

internal class WindowPreferences {
    private val preferences by lazy { Preferences.userRoot().node("/com/mkulikov/taskb/desktop") }

    fun load(): SavedWindowState? {
        val value = try {
            preferences.get("window", null)
        } catch (_: SecurityException) {
            null
        } ?: return null
        // The last field is unrestricted and may itself contain commas.
        val fields = value.split(',', limit = 6)
        if (fields.size != 6 || fields[5].isBlank()) return null
        val x = fields[0].toIntOrNull() ?: return null
        val y = fields[1].toIntOrNull() ?: return null
        val width = fields[2].toIntOrNull() ?: return null
        val height = fields[3].toIntOrNull() ?: return null
        val maximized = fields[4].toBooleanStrictOrNull() ?: return null
        if (width <= 0 || height <= 0) return null
        return SavedWindowState(
            screenId = fields[5],
            floatingBounds = Rectangle(x, y, width, height),
            maximized = maximized,
        )
    }

    fun save(state: SavedWindowState) {
        val bounds = state.floatingBounds
        val value = "${bounds.x},${bounds.y},${bounds.width},${bounds.height},${state.maximized},${state.screenId}"
        try {
            preferences.put("window", value)
        } catch (exception: SecurityException) {
            System.err.println("Unable to save the TaskB window settings: ${exception.message}")
        }
    }

    fun flush() {
        try {
            preferences.flush()
        } catch (exception: BackingStoreException) {
            System.err.println("Unable to flush the TaskB window settings: ${exception.message}")
        } catch (exception: SecurityException) {
            System.err.println("Unable to flush the TaskB window settings: ${exception.message}")
        }
    }
}
