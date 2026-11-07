package com.mkulikov.taskb.feature.holiday.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createHolidayHttpClient(): HttpClient? {
    return HttpClient(
        engineFactory = CIO,
    ) { configureHolidayClient() }
}
