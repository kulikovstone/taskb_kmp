package com.mkulikov.taskb.feature.task.component

import com.mkulikov.taskb.core.utils.time.dateLabel
import kotlinx.datetime.LocalDate

internal fun taskDateLabel(date: LocalDate, today: LocalDate, includeDate: Boolean = false): String =
    dateLabel(date, today, includeDate)
