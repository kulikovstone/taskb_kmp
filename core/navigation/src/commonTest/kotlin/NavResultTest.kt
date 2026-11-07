package com.mkulikov.taskb.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class NavResultTest {
    @Test
    fun `result waits for the calling screen to resume collecting`(): TestResult {
        return runTest {
            val navigator = AppNavigator().apply {
                setStartDestination(key = Caller)
                navigate(key = Picker)
            }
            navigator.createResultReceiver<Int>().use { receiver ->
                navigator.backWithResult(key = receiver.key, result = 7)
                assertEquals(expected = listOf<NavKey>(element = Caller), actual = navigator.backStack.toList())
                val received = mutableListOf<Int>()
                backgroundScope.launch { receiver.results.collect { result -> received.add(element = result) } }
                runCurrent()
                assertEquals(expected = listOf(element = 7), actual = received)
                navigator.navigate(key = Picker)
                navigator.back()
                runCurrent()
                assertEquals(expected = listOf(element = 7), actual = received)
            }
        }
    }

    @Test
    fun `results are scoped to a receiver and released when it closes`(): TestResult {
        return runTest {
            val navigator = AppNavigator().apply { setStartDestination(key = Caller) }
            val first = navigator.createResultReceiver<Int>()
            val second = navigator.createResultReceiver<String>()
            val firstResults = mutableListOf<Int>()
            val secondResults = mutableListOf<String>()
            val collector = backgroundScope.launch {
                first.results.collect { result -> firstResults.add(element = result) }
            }
            backgroundScope.launch { second.results.collect { result -> secondResults.add(element = result) } }
            runCurrent()
            navigator.navigate(key = Picker)
            navigator.backWithResult(key = second.key, result = "Work")
            runCurrent()
            assertEquals(expected = emptyList(), actual = firstResults)
            assertEquals(expected = listOf(element = "Work"), actual = secondResults)
            first.close()
            runCurrent()
            assertTrue(actual = collector.isCancelled)
            navigator.navigate(key = Picker)
            navigator.backWithResult(key = first.key, result = 7)
            runCurrent()
            assertEquals(expected = emptyList(), actual = firstResults)
            second.close()
        }
    }

    private data object Caller : NavKey

    private data object Picker : NavKey
}
