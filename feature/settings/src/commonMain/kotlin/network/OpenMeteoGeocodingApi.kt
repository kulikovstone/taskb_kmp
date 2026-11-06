package com.mkulikov.taskb.feature.settings.network

import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.*

internal class OpenMeteoGeocodingApi(private val client: HttpClient?) {
    suspend fun searchLocations(query: String): List<AppLocation> {
        val response = checkNotNull(client) { "Location search is not available on this platform yet." }
            .get("https://geocoding-api.open-meteo.com/v1/search") {
                parameter("name", query.trim())
                parameter("count", 10)
                parameter("language", "en")
                parameter("format", "json")
            }
        check(response.status.value in 200..299)
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return root["results"]?.jsonArray.orEmpty().mapNotNull { element ->
            runCatching {
                val item = element.jsonObject
                val regionName = item["admin1"]?.jsonPrimitive?.contentOrNull
                AppLocation(
                    name = listOfNotNull(item.getValue("name").jsonPrimitive.content, regionName,
                        item["country"]?.jsonPrimitive?.contentOrNull).distinct().joinToString(", "),
                    latitude = item.getValue("latitude").jsonPrimitive.double,
                    longitude = item.getValue("longitude").jsonPrimitive.double,
                    timeZone = item.getValue("timezone").jsonPrimitive.content,
                    countryCode = item["country_code"]?.jsonPrimitive?.contentOrNull?.uppercase(),
                    regionName = regionName,
                )
            }.getOrNull()
        }
    }
}
