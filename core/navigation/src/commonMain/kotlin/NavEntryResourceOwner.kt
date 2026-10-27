package com.mkulikov.taskb.core.navigation

import androidx.compose.runtime.RememberObserver

internal class NavEntryResourceOwner : RememberObserver, AutoCloseable {
    private val entries = mutableMapOf<Any, NavEntryResources>()
    private var closed = false

    fun resourcesFor(contentKey: Any): NavEntryResources {
        check(!closed) { "NavEntry resource owner is already closed" }
        return entries.getOrPut(contentKey) { NavEntryResources() }
    }

    fun remove(contentKey: Any) {
        entries.remove(contentKey)?.close()
    }

    override fun close() {
        if (closed) return
        closed = true
        val toClose = entries.values.toList()
        entries.clear()
        closeAll(toClose)
    }

    override fun onRemembered() = Unit
    override fun onForgotten() = close()
    override fun onAbandoned() = close()
}
