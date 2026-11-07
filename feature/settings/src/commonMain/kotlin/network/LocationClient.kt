package com.mkulikov.taskb.feature.settings.network

internal class LocationClient : AutoCloseable {
    val client = createLocationHttpClient()

    override fun close() {
        client?.close()
    }
}
