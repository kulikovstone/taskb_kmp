package com.mkulikov.taskb.feature.holiday.network

internal class HolidayClient : AutoCloseable {
    val client = createHolidayHttpClient()

    override fun close() {
        client?.close()
    }
}
