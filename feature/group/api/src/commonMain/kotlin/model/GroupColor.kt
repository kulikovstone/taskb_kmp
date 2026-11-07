package com.mkulikov.taskb.feature.group.api.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mkulikov.taskb.ui.theme.Palette

enum class GroupColor(val label: String, val color: Color) {
    Red(label = "Red", color = Palette.Red.s400),
    Pink(label = "Pink", color = Palette.Pink.s400),
    Purple(label = "Purple", color = Palette.Purple.s400),
    DeepPurple(label = "Deep purple", color = Palette.DeepPurple.s400),
    Indigo(label = "Indigo", color = Palette.Indigo.s400),
    Blue(label = "Blue", color = Palette.Blue.s400),
    LightBlue(label = "Light blue", color = Palette.LightBlue.s400),
    Cyan(label = "Cyan", color = Palette.Cyan.s400),
    Teal(label = "Teal", color = Palette.Teal.s400),
    Green(label = "Green", color = Palette.Green.s400),
    LightGreen(label = "Light green", color = Palette.LightGreen.s400),
    Lime(label = "Lime", color = Palette.Lime.s400),
    Yellow(label = "Yellow", color = Palette.Yellow.s400),
    Amber(label = "Amber", color = Palette.Amber.s400),
    Orange(label = "Orange", color = Palette.Orange.s400),
    DeepOrange(label = "Deep orange", color = Palette.DeepOrange.s400),
    Brown(label = "Brown", color = Palette.Brown.s400),
    Grey(label = "Grey", color = Palette.Grey.s400),
    BlueGrey(label = "Blue grey", color = Palette.BlueGrey.s400);

    companion object {
        fun fromArgb(argb: Int): GroupColor = entries.firstOrNull { color -> color.color.toArgb() == argb } ?: Grey
    }
}
