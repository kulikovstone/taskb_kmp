package com.mkulikov.taskb.feature.task.time

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

internal fun currentDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
