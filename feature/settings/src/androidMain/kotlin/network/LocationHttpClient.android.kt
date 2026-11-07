package com.mkulikov.taskb.feature.settings.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createLocationHttpClient(): HttpClient? {
    return HttpClient(
        engineFactory = CIO,
    ) { configureLocationClient() }
}
