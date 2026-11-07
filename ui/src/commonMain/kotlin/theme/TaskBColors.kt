package com.mkulikov.taskb.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

@Immutable
data class TaskBColors(
    val surface: Color,
    val text: Color,
    val heading: Color,
    val secondary: Color,
    val muted: Color,
    val taskTime: Color,
    val completedText: Color,
    val clearIcon: Color,
    val border: Color,
    val subtle: Color,
    val accent: Color,
    val actionFill: Color,
    val onAction: Color,
    val selected: Color,
    val calendarSelectedDay: Color,
    val overdueTaskBackground: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val scrim: Color,
    val inverseSurface: Color,
    val onInverseSurface: Color,
    val inverseAccent: Color,
)

internal val LightTaskBColors = TaskBColors(
    surface = Palette.White,
    text = Palette.BlueGrey.s900,
    heading = Palette.Grey.s900,
    secondary = Palette.BlueGrey.s700,
    muted = Palette.BlueGrey.s500,
    taskTime = Palette.BlueGrey.s600,
    completedText = Palette.Grey.s400,
    clearIcon = Palette.BlueGrey.s100,
    border = Palette.BlueGrey.s50,
    subtle = Palette.Grey.s50,
    accent = Palette.Blue.a700,
    actionFill = Palette.Blue.a700,
    onAction = Palette.White,
    selected = Palette.Blue.s50,
    calendarSelectedDay = Palette.Green.s50,
    overdueTaskBackground = Palette.Red.s50,
    danger = Palette.Red.s900,
    dangerSoft = Palette.Red.s50,
    success = Palette.Green.s600,
    scrim = Palette.Black.copy(alpha = 0.32f),
    inverseSurface = Palette.BlueGrey.s900,
    onInverseSurface = Palette.White,
    inverseAccent = Palette.Blue.a100,
)

internal val DarkTaskBColors = TaskBColors(
    surface = Palette.Grey.s900,
    text = Palette.BlueGrey.s50,
    heading = Palette.BlueGrey.s50,
    secondary = Palette.BlueGrey.s100,
    muted = Palette.BlueGrey.s300,
    taskTime = Palette.BlueGrey.s300,
    completedText = Palette.Grey.s400,
    clearIcon = Palette.BlueGrey.s50,
    border = Palette.BlueGrey.s800,
    subtle = Palette.BlueGrey.s900,
    accent = Palette.Blue.a100,
    actionFill = Palette.Blue.a700,
    onAction = Palette.White,
    selected = Palette.BlueGrey.s800,
    calendarSelectedDay = Palette.BlueGrey.s800,
    overdueTaskBackground = Palette.Red.s900.copy(alpha = 0.16f).compositeOver(background = Palette.Grey.s900),
    danger = Palette.Red.a100,
    dangerSoft = Palette.Red.s900,
    success = Palette.Green.s300,
    scrim = Palette.Black.copy(alpha = 0.32f),
    inverseSurface = Palette.BlueGrey.s50,
    onInverseSurface = Palette.BlueGrey.s900,
    inverseAccent = Palette.Blue.a700,
)

internal val LocalTaskBColors = staticCompositionLocalOf<TaskBColors> {
    error(message = "TaskBTheme is not present")
}
