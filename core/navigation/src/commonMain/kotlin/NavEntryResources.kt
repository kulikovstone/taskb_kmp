package com.mkulikov.taskb.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntryDecorator

/** Keeps a resource alive while its Navigation 3 content key remains in the back stack. */
@Composable
fun <T : AutoCloseable> rememberNavEntryResource(key: Any, factory: () -> T): T {
    val resources = LocalNavEntryResources.current
    return remember(key1 = resources, key2 = key) { resources.getOrCreate(key = key, factory = factory) }
}

@Composable
fun <T : Any> rememberEntryResourceNavEntryDecorator(): NavEntryDecorator<T> {
    val owner = remember { NavEntryResourceOwner() }
    return remember(key1 = owner) {
        NavEntryDecorator(
            onPop = owner::remove,
            decorate = { entry ->
                CompositionLocalProvider(
                    value = LocalNavEntryResources provides owner.resourcesFor(contentKey = entry.contentKey)
                ) {
                    entry.Content()
                }
            },
        )
    }
}

private val LocalNavEntryResources = staticCompositionLocalOf<NavEntryResources> {
    error(message = "NavEntry resources require rememberEntryResourceNavEntryDecorator")
}

internal class NavEntryResources : AutoCloseable {
    private val resources = mutableMapOf<Any, AutoCloseable>()
    private var closed = false

    @Suppress("UNCHECKED_CAST")
    fun <T : AutoCloseable> getOrCreate(key: Any, factory: () -> T): T {
        check(value = !closed) { "NavEntry resources are already closed" }
        return resources.getOrPut(key = key, defaultValue = factory) as T
    }

    override fun close() {
        if (closed) return
        closed = true
        val toClose = resources.values.toList()
        resources.clear()
        closeAll(resources = toClose)
    }
}

internal fun closeAll(resources: List<AutoCloseable>) {
    var failure: Throwable? = null
    resources.asReversed().forEach { resource ->
        try {
            resource.close()
        } catch (exception: Throwable) {
            val previousFailure = failure
            if (previousFailure == null) failure = exception else previousFailure.addSuppressed(exception = exception)
        }
    }
    failure?.let { exception -> throw exception }
}
