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
        require(name.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(countryCode == null || countryCode.matches(Regex("[A-Z]{2}")))
        TimeZone.of(timeZone)
    }
}
