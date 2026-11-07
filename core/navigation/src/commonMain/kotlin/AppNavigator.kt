package com.mkulikov.taskb.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey

@Stable
internal class AppNavigator : IAppNavigator {
    val backStack = mutableStateListOf<NavKey>()
    private val resultReceivers = mutableMapOf<NavResultKey<*>, NavResultReceiver<*>>()

    fun setStartDestination(key: NavKey) {
        if (backStack.isEmpty()) backStack.add(element = key)
    }

    override fun navigate(key: NavKey) {
        backStack.add(element = key)
    }

    override fun replaceTop(key: NavKey) {
        if (backStack.isEmpty()) backStack.add(element = key) else backStack[backStack.lastIndex] = key
    }

    override fun popUpTo(key: NavKey, inclusive: Boolean) {
        val index = backStack.indexOfLast { resultKey -> resultKey == key }
        if (index < 0) return
        val size = (index + if (inclusive) 0 else 1).coerceAtLeast(minimumValue = 1)
        Snapshot.withMutableSnapshot {
            while (backStack.size > size) backStack.removeLast()
        }
    }

    override fun replaceAll(key: NavKey) {
        Snapshot.withMutableSnapshot {
            backStack.clear()
            backStack.add(element = key)
        }
    }

    override fun back() {
        if (backStack.size > 1) backStack.removeLast()
    }

    override fun <T : Any> createResultReceiver(): INavResultReceiver<T> {
        return NavResultReceiver<T> { key ->
            resultReceivers.remove(key = key)
        }.also { resultReceiver -> resultReceivers[resultReceiver.key] = resultReceiver }
    }

    override fun <T : Any> backWithResult(key: NavResultKey<T>, result: T) {
        if (backStack.size <= 1) return
        // A key is created together with its receiver and cannot be reused for another type.
        @Suppress("UNCHECKED_CAST") val receiver = resultReceivers[key] as? NavResultReceiver<T>
        receiver?.deliver(result = result)
        back()
    }
}
