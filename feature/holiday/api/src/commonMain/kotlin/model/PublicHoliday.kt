package com.mkulikov.taskb.feature.holiday.api.model

import kotlinx.datetime.LocalDate

/** A public holiday supplied by the configured jurisdiction's holiday service. */
data class PublicHoliday(val id: String, val date: LocalDate, val name: String, val countryCode: String)
