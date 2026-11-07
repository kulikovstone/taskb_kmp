package com.mkulikov.taskb.core.mvi

import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineStoreTest {
    @Test
    fun `flow updates use the reducer and subscription ends with the store`(): TestResult {
        return runTest {
            val updates = MutableSharedFlow<Int>()
            val store =
                object : CoroutineStore<Unit, Int, Int>(
                    initialState = 0,
                    reducer = Int::plus,
                    context = StandardTestDispatcher(scheduler = testScheduler),
                ) {
                    override fun handleIntent(intent: Unit) {
                        collectEffect(messages = updates)
                    }
                }
            try {
                store.accept(intent = Unit)
                runCurrent()
                assertEquals(expected = 1, actual = updates.subscriptionCount.value)
                updates.emit(value = 2)
                runCurrent()
                updates.emit(value = 3)
                runCurrent()
                assertEquals(expected = 5, actual = store.state.value)

                store.close()
                advanceUntilIdle()
                assertEquals(expected = 0, actual = updates.subscriptionCount.value)
                updates.emit(value = 100)
                advanceUntilIdle()
                assertEquals(expected = 5, actual = store.state.value)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun `effects do not block intents and reduce against the latest state`(): TestResult {
        return runTest {
            val gate = CompletableDeferred<Unit>()
            CounterStore(context = StandardTestDispatcher(scheduler = testScheduler)).use { store ->
                store.accept(intent = Intent.Add(amount = 1))
                store.accept(intent = Intent.AddLater(amount = 100, gate = gate))
                store.accept(intent = Intent.Add(amount = 2))
                store.accept(intent = Intent.Add(amount = 3))
                runCurrent()
                assertEquals(expected = 6, actual = store.state.value)

                gate.complete(value = Unit)
                advanceUntilIdle()
                assertEquals(expected = 106, actual = store.state.value)
            }
        }
    }

    @Test
    fun `concurrent producers do not lose state updates`(): TestResult {
        return runTest {
            CounterStore(context = StandardTestDispatcher(scheduler = testScheduler)).use { store ->
                coroutineScope {
                    repeat(times = 8) {
                        launch(context = Dispatchers.Default) {
                            repeat(times = 200) { store.accept(intent = Intent.Add(amount = 1)) }
                        }
                    }
                }
                advanceUntilIdle()
                assertEquals(expected = 1600, actual = store.state.value)
            }
        }
    }

    @Test
    fun `close cancels all effects and discards queued and future intents`(): TestResult {
        return runTest {
            var cancellations = 0
            val store = CounterStore(context = StandardTestDispatcher(scheduler = testScheduler)) { cancellations++ }
            try {
                repeat(times = 2) { store.accept(intent = Intent.Wait) }
                runCurrent()
                store.accept(intent = Intent.Add(amount = 10))
                store.close()
                store.close()
                store.accept(intent = Intent.Add(amount = 100))
                advanceUntilIdle()
                assertEquals(expected = 2, actual = cancellations)
                assertEquals(expected = 0, actual = store.state.value)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun `close before first dispatch prevents intent processing`(): TestResult {
        return runTest {
            val store = CounterStore(context = StandardTestDispatcher(scheduler = testScheduler))
            store.accept(intent = Intent.Add(amount = 1))
            store.close()
            advanceUntilIdle()
            assertEquals(expected = 0, actual = store.state.value)
        }
    }

    @Test
    fun `store has an owned job and respects parent cancellation`(): TestResult {
        return runTest {
            val parent = Job()
            val context = StandardTestDispatcher(scheduler = testScheduler) + parent
            val sibling = CoroutineScope(context = context).launch { awaitCancellation() }
            var cancellations = 0
            val first = CounterStore(context = context) { cancellations++ }
            val second = CounterStore(context = context) { cancellations++ }
            try {
                first.accept(intent = Intent.Wait)
                second.accept(intent = Intent.Wait)
                runCurrent()
                first.close()
                runCurrent()
                assertEquals(expected = 1, actual = cancellations)
                assertTrue(actual = parent.isActive)
                assertTrue(actual = sibling.isActive)

                parent.cancel()
                advanceUntilIdle()
                assertEquals(expected = 2, actual = cancellations)
                assertFalse(actual = sibling.isActive)
                second.accept(intent = Intent.Add(amount = 1))
                advanceUntilIdle()
                assertEquals(expected = 0, actual = second.state.value)
            } finally {
                first.close()
                second.close()
                parent.cancel()
            }
        }
    }

    @Test
    fun `unhandled failures stop the store and cancel sibling effects`(): TestResult {
        return runTest {
            FailurePoint.entries.forEach { point ->
                val failures = mutableListOf<Throwable>()
                var cancelled = false
                val context = StandardTestDispatcher(scheduler = testScheduler) +
                    CoroutineExceptionHandler { _, failure -> failures.add(element = failure) }
                val store =
                    object : CoroutineStore<Intent, Int, Int>(
                        initialState = 0,
                        reducer = { state, message ->
                            if (message == -1) error(message = "Reducer failure")
                            state + message
                        },
                        context = context,
                    ) {
                        override fun handleIntent(intent: Intent) {
                            when (intent) {
                                Intent.Wait -> launchEffect {
                                    try {
                                        awaitCancellation()
                                    } finally {
                                        cancelled = true
                                    }
                                }
                                Intent.Crash -> when (point) {
                                    FailurePoint.Intent -> error(message = "Intent failure")
                                    FailurePoint.Reducer -> reduce(message = -1)
                                    FailurePoint.Effect -> launchEffect { error(message = "Effect failure") }
                                }
                                is Intent.Add -> reduce(message = intent.amount)
                                is Intent.AddLater -> error(message = "Not used in this test")
                            }
                        }
                    }
                store.use {
                    store.accept(intent = Intent.Wait)
                    runCurrent()
                    store.accept(intent = Intent.Crash)
                    advanceUntilIdle()
                    assertEquals(expected = 1, actual = failures.size, message = point.name)
                    assertTrue(actual = cancelled, message = point.name)
                    store.accept(intent = Intent.Add(amount = 1))
                    advanceUntilIdle()
                    assertEquals(expected = 0, actual = store.state.value, message = point.name)
                }
            }
        }
    }

    private class CounterStore(
        context: CoroutineContext,
        private val onCancelled: () -> Unit = {},
    ) : CoroutineStore<Intent, Int, Int>(
        initialState = 0,
        reducer = Int::plus,
        context = context,
    ) {
        override fun handleIntent(intent: Intent) {
            when (intent) {
                is Intent.Add -> reduce(message = intent.amount)
                is Intent.AddLater -> launchEffect {
                    intent.gate.await()
                    intent.amount
                }
                Intent.Wait -> launchEffect {
                    try {
                        awaitCancellation()
                    } finally {
                        onCancelled()
                    }
                }
                Intent.Crash -> error(message = "Not used in this test")
            }
        }
    }

    private sealed interface Intent {
        data class Add(val amount: Int) : Intent

        data class AddLater(val amount: Int, val gate: Deferred<Unit>) : Intent

        data object Wait : Intent

        data object Crash : Intent
    }

    private enum class FailurePoint {
        Intent,
        Reducer,
        Effect,
    }
}
