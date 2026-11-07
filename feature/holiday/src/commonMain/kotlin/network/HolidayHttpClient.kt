package com.mkulikov.taskb.feature.holiday.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

internal expect fun createHolidayHttpClient(): HttpClient?

internal fun HttpClientConfig<*>.configureHolidayClient() {
    expectSuccess = true
    install(plugin = HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}
