package com.mkulikov.taskb.feature.settings.api.model

import kotlinx.datetime.TimeZone

/** A user-selected application location shared by weather and public holidays. */
data class AppLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val countryCode: String? = null,
    val regionName: String? = null,
) {
    init {
        require(value = name.isNotBlank())
        require(value = latitude.isFinite() && latitude in -90.0..90.0)
        require(value = longitude.isFinite() && longitude in -180.0..180.0)
        require(value = countryCode == null || countryCode.matches(regex = Regex(pattern = "[A-Z]{2}")))
        TimeZone.of(zoneId = timeZone)
    }
}
