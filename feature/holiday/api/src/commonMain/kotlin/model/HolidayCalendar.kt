package com.mkulikov.taskb.feature.holiday.api.model

import com.mkulikov.taskb.feature.settings.api.model.AppLocation

data class HolidayCalendar(
    val location: AppLocation? = null,
    val holidays: List<PublicHoliday> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
