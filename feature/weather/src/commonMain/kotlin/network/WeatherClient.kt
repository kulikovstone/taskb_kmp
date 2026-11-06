package com.mkulikov.taskb.feature.weather.network

/** Owns the platform client so Koin can close it with the application graph. */
internal class WeatherClient : AutoCloseable {
    val client = createWeatherHttpClient()
    override fun close() { client?.close() }
}
