package com.mkulikov.taskb.feature.calendar.api

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate

data class CalendarDayRoute(val date: LocalDate) : NavKey
