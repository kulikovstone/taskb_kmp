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
        val response = requireClient().get(urlString = "https://api.open-meteo.com/v1/forecast") {
            parameter(key = "latitude", value = location.latitude)
            parameter(key = "longitude", value = location.longitude)
            parameter(key = "timezone", value = location.timeZone)
            parameter(key = "forecast_days", value = 10)
            parameter(key = "temperature_unit", value = "celsius")
            parameter(key = "daily", value = "weather_code,temperature_2m_min,temperature_2m_max")
        }
        check(value = response.status.value in 200..299)
        val daily = Json.parseToJsonElement(string = response.bodyAsText()).jsonObject.getValue(
            key = "daily",
        ).jsonObject
        val dates = daily.getValue(key = "time").jsonArray
        val codes = daily.getValue(key = "weather_code").jsonArray
        val minimums = daily.getValue(key = "temperature_2m_min").jsonArray
        val maximums = daily.getValue(key = "temperature_2m_max").jsonArray
        require(value = listOf(codes, minimums, maximums).all { array -> array.size == dates.size })
        return dates.indices
            .mapNotNull { index ->
                val minimum = minimums[index].jsonPrimitive.doubleOrNull
                val maximum = maximums[index].jsonPrimitive.doubleOrNull
                if (
                    minimum == null ||
                        maximum == null ||
                        !minimum.isFinite() ||
                        !maximum.isFinite() ||
                        minimum > maximum
                ) {
                    null
                } else {
                    DailyWeather(
                        date = LocalDate.parse(input = dates[index].jsonPrimitive.content),
                        condition = weatherCondition(code = codes[index].jsonPrimitive.intOrNull),
                        minimumCelsius = minimum,
                        maximumCelsius = maximum,
                    )
                }
            }
            .distinctBy(selector = DailyWeather::date)
            .sortedBy(selector = DailyWeather::date)
            .take(n = 10)
    }

    private fun requireClient() = checkNotNull(value = client) { "Weather is not available on this platform yet." }
}

internal fun weatherCondition(code: Int?): WeatherCondition {
    return when (code) {
        0,
        1 -> WeatherCondition.Clear
        2 -> WeatherCondition.PartlyCloudy
        3 -> WeatherCondition.Cloudy
        45,
        48 -> WeatherCondition.Fog
        51,
        53,
        55,
        56,
        57 -> WeatherCondition.Drizzle
        61,
        63,
        66,
        67,
        80,
        81 -> WeatherCondition.Rain
        65,
        82 -> WeatherCondition.HeavyRain
        71,
        73,
        75,
        77,
        85,
        86 -> WeatherCondition.Snow
        95,
        96,
        97,
        99 -> WeatherCondition.Thunderstorm
        else -> WeatherCondition.Unknown
    }
}
