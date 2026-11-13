package com.mkulikov.taskb.feature.calendar.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate

internal data class CalendarDayRoute(val date: LocalDate) : NavKey
