package com.mkulikov.taskb.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NavResultTest {
    @Test
    fun `result waits for the calling screen to resume collecting`() = runTest {
        val navigator = AppNavigator().apply { setStartDestination(Caller); navigate(Picker) }
        navigator.createResultReceiver<Int>().use { receiver ->
            navigator.backWithResult(receiver.key, 7)
            assertEquals(listOf<NavKey>(Caller), navigator.backStack.toList())
            val received = mutableListOf<Int>()
            backgroundScope.launch { receiver.results.collect { received.add(it) } }
            runCurrent()
            assertEquals(listOf(7), received)
            navigator.navigate(Picker)
            navigator.back()
            runCurrent()
            assertEquals(listOf(7), received)
        }
    }

    @Test
    fun `results are scoped to a receiver and released when it closes`() = runTest {
        val navigator = AppNavigator().apply { setStartDestination(Caller) }
        val first = navigator.createResultReceiver<Int>()
        val second = navigator.createResultReceiver<String>()
        val firstResults = mutableListOf<Int>()
        val secondResults = mutableListOf<String>()
        val collector = backgroundScope.launch { first.results.collect { firstResults.add(it) } }
        backgroundScope.launch { second.results.collect { secondResults.add(it) } }
        runCurrent()
        navigator.navigate(Picker)
        navigator.backWithResult(second.key, "Work")
        runCurrent()
        assertEquals(emptyList(), firstResults)
        assertEquals(listOf("Work"), secondResults)
        first.close()
        runCurrent()
        assertTrue(collector.isCancelled)
        navigator.navigate(Picker)
        navigator.backWithResult(first.key, 7)
        runCurrent()
        assertEquals(emptyList(), firstResults)
        second.close()
    }

    private data object Caller : NavKey
    private data object Picker : NavKey
}
