package com.mkulikov.taskb.feature.settings.network

import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.*

internal class OpenMeteoGeocodingApi(private val client: HttpClient?) {
    suspend fun searchLocations(query: String): List<AppLocation> {
        val response = checkNotNull(value = client) { "Location search is not available on this platform yet." }
            .get(urlString = "https://geocoding-api.open-meteo.com/v1/search") {
                parameter(key = "name", value = query.trim())
                parameter(key = "count", value = 10)
                parameter(key = "language", value = "en")
                parameter(key = "format", value = "json")
            }
        check(value = response.status.value in 200..299)
        val root = Json.parseToJsonElement(string = response.bodyAsText()).jsonObject
        return root["results"]?.jsonArray.orEmpty().mapNotNull { element ->
            runCatching {
                val item = element.jsonObject
                val regionName = item["admin1"]?.jsonPrimitive?.contentOrNull
                AppLocation(
                    name = listOfNotNull(
                            item.getValue(key = "name").jsonPrimitive.content,
                            regionName,
                            item["country"]?.jsonPrimitive?.contentOrNull,
                        )
                        .distinct()
                        .joinToString(separator = ", "),
                    latitude = item.getValue(key = "latitude").jsonPrimitive.double,
                    longitude = item.getValue(key = "longitude").jsonPrimitive.double,
                    timeZone = item.getValue(key = "timezone").jsonPrimitive.content,
                    countryCode = item["country_code"]?.jsonPrimitive?.contentOrNull?.uppercase(),
                    regionName = regionName,
                )
            }
                .getOrNull()
        }
    }
}
