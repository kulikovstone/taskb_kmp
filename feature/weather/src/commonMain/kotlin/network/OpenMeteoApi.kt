package com.mkulikov.taskb.feature.weather.network

import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherCondition
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*

internal class OpenMeteoApi(private val client: HttpClient?) {
    suspend fun forecast(location: WeatherLocation): List<DailyWeather> {
        val response = requireClient().get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("timezone", location.timeZone)
            parameter("forecast_days", 10)
            parameter("temperature_unit", "celsius")
            parameter("daily", "weather_code,temperature_2m_min,temperature_2m_max")
        }
        check(response.status.value in 200..299)
        val daily = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("daily").jsonObject
        val dates = daily.getValue("time").jsonArray
        val codes = daily.getValue("weather_code").jsonArray
        val minimums = daily.getValue("temperature_2m_min").jsonArray
        val maximums = daily.getValue("temperature_2m_max").jsonArray
        require(listOf(codes, minimums, maximums).all { it.size == dates.size })
        return dates.indices.mapNotNull { index ->
            val minimum = minimums[index].jsonPrimitive.doubleOrNull
            val maximum = maximums[index].jsonPrimitive.doubleOrNull
            if (minimum == null || maximum == null || !minimum.isFinite() || !maximum.isFinite() || minimum > maximum) null
            else DailyWeather(LocalDate.parse(dates[index].jsonPrimitive.content),
                weatherCondition(codes[index].jsonPrimitive.intOrNull), minimum, maximum)
        }.distinctBy { it.date }.sortedBy { it.date }.take(10)
    }

    private fun requireClient() = checkNotNull(client) { "Weather is not available on this platform yet." }
}

internal fun weatherCondition(code: Int?): WeatherCondition = when (code) {
    0, 1 -> WeatherCondition.Clear
    2 -> WeatherCondition.PartlyCloudy
    3 -> WeatherCondition.Cloudy
    45, 48 -> WeatherCondition.Fog
    51, 53, 55, 56, 57 -> WeatherCondition.Drizzle
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.Rain
    71, 73, 75, 77, 85, 86 -> WeatherCondition.Snow
    95, 96, 97, 99 -> WeatherCondition.Thunderstorm
    else -> WeatherCondition.Unknown
}
