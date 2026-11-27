package com.mkulikov.taskb.feature.settings.api.model

enum class ThemeMode(val label: String) {
    System(label = "System"),
    Light(label = "Light"),
    Dark(label = "Dark"),
    ;

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        System -> systemIsDark
        Light -> false
        Dark -> true
    }
}
