package com.mkulikov.taskb.feature.hello.screen.hello

import com.mkulikov.taskb.feature.settings.api.IAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class HelloStoreTest {
    @Test
    fun `successful save publishes progress and completion`(): TestResult {
        return runTest {
            val gate = CompletableDeferred<Unit>()
            val settings = FakeSettings { gate.await() }
            HelloStore(
                    settings = settings,
                    initialError = "Initial error",
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    assertEquals(expected = "Initial error", actual = store.state.value.error)
                    store.accept(intent = HelloIntent.Continue)
                    runCurrent()
                    assertTrue(actual = store.state.value.isContinuing)
                    assertEquals(expected = null, actual = store.state.value.error)
                    assertFalse(actual = store.state.value.isCompleted)

                    gate.complete(value = Unit)
                    advanceUntilIdle()
                    assertTrue(actual = store.state.value.isCompleted)
                    assertFalse(actual = store.state.value.isContinuing)
                    assertEquals(expected = 1, actual = settings.calls)
                }
        }
    }

    @Test
    fun `failed save allows an explicit retry`(): TestResult {
        return runTest {
            var fail = true
            val settings = FakeSettings { if (fail) error(message = "Storage failure") }
            HelloStore(settings = settings, dispatcher = StandardTestDispatcher(scheduler = testScheduler)).use { store
                ->
                store.accept(intent = HelloIntent.Continue)
                advanceUntilIdle()
                assertFalse(actual = store.state.value.isContinuing)
                assertFalse(actual = store.state.value.isCompleted)
                assertEquals(expected = "Couldn't save settings. Please try again.", actual = store.state.value.error)

                fail = false
                store.accept(intent = HelloIntent.Continue)
                advanceUntilIdle()
                assertEquals(expected = 2, actual = settings.calls)
                assertTrue(actual = store.state.value.isCompleted)
                assertEquals(expected = null, actual = store.state.value.error)
            }
        }
    }

    @Test
    fun `repeated intents do not duplicate an active or completed save`(): TestResult {
        return runTest {
            val gate = CompletableDeferred<Unit>()
            val settings = FakeSettings { gate.await() }
            HelloStore(settings = settings, dispatcher = StandardTestDispatcher(scheduler = testScheduler)).use { store
                ->
                repeat(times = 10) { store.accept(intent = HelloIntent.Continue) }
                runCurrent()
                repeat(times = 10) { store.accept(intent = HelloIntent.Continue) }
                runCurrent()
                assertEquals(expected = 1, actual = settings.calls)

                gate.complete(value = Unit)
                advanceUntilIdle()
                store.accept(intent = HelloIntent.Continue)
                advanceUntilIdle()
                assertEquals(expected = 1, actual = settings.calls)
            }
        }
    }

    @Test
    fun `closing store cancels saving and stops processing intents`(): TestResult {
        return runTest {
            var cancelled = false
            val settings = FakeSettings {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            val store = HelloStore(settings = settings, dispatcher = StandardTestDispatcher(scheduler = testScheduler))
            try {
                store.accept(intent = HelloIntent.Continue)
                runCurrent()
                assertEquals(expected = 1, actual = settings.calls)
                store.close()
                runCurrent()
                assertTrue(actual = cancelled)
                assertFalse(actual = store.state.value.isCompleted)

                store.accept(intent = HelloIntent.Continue)
                advanceUntilIdle()
                assertEquals(expected = 1, actual = settings.calls)
            } finally {
                store.close()
            }
        }
    }

    private class FakeSettings(private val save: suspend () -> Unit) : IAppSettings {
        var calls = 0

        override suspend fun hasCompletedWelcome() = false

        override suspend fun completeWelcome() {
            calls++
            save()
        }
    }
}
