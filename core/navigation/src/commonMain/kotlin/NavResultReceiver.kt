package com.mkulikov.taskb.core.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal class NavResultReceiver<T : Any>(private val onClose: (NavResultKey<T>) -> Unit) : INavResultReceiver<T> {
    override val key = NavResultKey<T>()
    private val channel = Channel<T>(capacity = Channel.UNLIMITED)
    override val results = channel.receiveAsFlow()

    fun deliver(result: T) {
        channel.trySend(element = result)
    }

    override fun close() {
        onClose(key)
        channel.cancel()
    }
}
