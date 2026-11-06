package com.mkulikov.taskb.feature.weather.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createWeatherHttpClient(): HttpClient? = HttpClient(CIO) { configureWeatherClient() }
