package com.mkulikov.taskb.ui.icon

import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Add_task
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Arrow_downward
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Calendar_add_on
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Check_box_outline_blank
import com.composables.icons.materialsymbols.outlined.Chevron_left
import com.composables.icons.materialsymbols.outlined.Chevron_right
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Filter_alt
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Keyboard
import com.composables.icons.materialsymbols.outlined.Keyboard_double_arrow_left
import com.composables.icons.materialsymbols.outlined.Keyboard_double_arrow_right
import com.composables.icons.materialsymbols.outlined.Repeat
import com.composables.icons.materialsymbols.outlined.Schedule
import com.composables.icons.materialsymbols.outlined.Search
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Today
import com.composables.icons.materialsymbols.outlinedfilled.Cancel
import com.composables.icons.materialsymbols.outlinedfilled.Check_box
import com.composables.icons.materialsymbols.outlinedfilled.Folder
import com.mkulikov.taskb.ui.resources.Res
import com.mkulikov.taskb.ui.resources.weather_cloudy
import com.mkulikov.taskb.ui.resources.weather_drizzle
import com.mkulikov.taskb.ui.resources.weather_fog
import com.mkulikov.taskb.ui.resources.weather_heavy_rain
import com.mkulikov.taskb.ui.resources.weather_partly_cloudy
import com.mkulikov.taskb.ui.resources.weather_rain
import com.mkulikov.taskb.ui.resources.weather_snow
import com.mkulikov.taskb.ui.resources.weather_sunny
import com.mkulikov.taskb.ui.resources.weather_thunderstorm
import com.mkulikov.taskb.ui.resources.weather_unknown

object TaskBIcons {
    val Sunny
        get() = Res.drawable.weather_sunny

    val PartlyCloudy
        get() = Res.drawable.weather_partly_cloudy

    val Cloudy
        get() = Res.drawable.weather_cloudy

    val Fog
        get() = Res.drawable.weather_fog

    val Drizzle
        get() = Res.drawable.weather_drizzle

    val Rain
        get() = Res.drawable.weather_rain

    val HeavyRain
        get() = Res.drawable.weather_heavy_rain

    val Snow
        get() = Res.drawable.weather_snow

    val Thunderstorm
        get() = Res.drawable.weather_thunderstorm

    val UnknownWeather
        get() = Res.drawable.weather_unknown

    val Settings
        get() = MaterialSymbols.Outlined.Settings

    val Back
        get() = MaterialSymbols.Outlined.Arrow_back

    val Add
        get() = MaterialSymbols.Outlined.Add

    val AddTask
        get() = MaterialSymbols.Outlined.Add_task

    val AddEvent
        get() = MaterialSymbols.Outlined.Calendar_add_on

    val Done
        get() = MaterialSymbols.Outlined.Check

    val Clear
        get() = MaterialSymbols.Outlined.Close

    val Delete
        get() = MaterialSymbols.Outlined.Delete

    val Sort
        get() = MaterialSymbols.Outlined.Arrow_downward

    val Filter
        get() = MaterialSymbols.Outlined.Filter_alt

    val Folder
        get() = MaterialSymbols.Outlined.Folder

    val FolderFilled
        get() = MaterialSymbols.OutlinedFilled.Folder

    val Search
        get() = MaterialSymbols.Outlined.Search

    val Cancel
        get() = MaterialSymbols.OutlinedFilled.Cancel

    val Calendar
        get() = MaterialSymbols.Outlined.Today

    val Repeat
        get() = MaterialSymbols.Outlined.Repeat

    val Schedule
        get() = MaterialSymbols.Outlined.Schedule

    val Keyboard
        get() = MaterialSymbols.Outlined.Keyboard

    val ChevronRight
        get() = MaterialSymbols.Outlined.Chevron_right

    val ChevronLeft
        get() = MaterialSymbols.Outlined.Chevron_left

    val DoubleChevronLeft
        get() = MaterialSymbols.Outlined.Keyboard_double_arrow_left

    val DoubleChevronRight
        get() = MaterialSymbols.Outlined.Keyboard_double_arrow_right

    val Dropdown
        get() = MaterialSymbols.Outlined.Arrow_drop_down

    val Checked
        get() = MaterialSymbols.OutlinedFilled.Check_box

    val Unchecked
        get() = MaterialSymbols.Outlined.Check_box_outline_blank
}
