package com.mkulikov.taskb.feature.settings.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

internal expect fun createLocationHttpClient(): HttpClient?

internal fun HttpClientConfig<*>.configureLocationClient() {
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}
