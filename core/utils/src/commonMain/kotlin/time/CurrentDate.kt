package com.mkulikov.taskb.core.utils.time

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

fun currentDate(): LocalDate = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault())
