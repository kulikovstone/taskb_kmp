package com.mkulikov.taskb.feature.weather.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

// iOS and Web retain their scaffold until networking on these targets is requested.
internal expect fun createWeatherHttpClient(): HttpClient?

internal fun HttpClientConfig<*>.configureWeatherClient() {
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}
