package com.mkulikov.taskb.core.mvi

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Processes intents and effect results serially. Each instance owns its coroutine scope. Recoverable failures
 * should be converted to messages by the feature; an unhandled failure cancels the store and its remaining work.
 */
abstract class CoroutineStore<Intent : Any, State : Any, Message : Any>(
    initialState: State,
    private val reducer: (State, Message) -> State,
    context: CoroutineContext = Dispatchers.Default,
) : IStore<Intent, State> {
    private val job = Job(parent = context[Job])
    private val scope = CoroutineScope(context = context + job)
    private val events = Channel<Event<Intent, Message>>(capacity = Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(value = initialState)
    final override val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        job.invokeOnCompletion { events.cancel() }
        scope.launch {
            use {
                for (event in events) {
                    ensureActive()
                    when (event) {
                        is Event.Input -> handleIntent(intent = event.intent)
                        is Event.Result -> reduce(message = event.message)
                    }
                }
            }
        }
    }

    final override fun accept(intent: Intent) {
        events.trySend(element = Event.Input(intent = intent))
    }

    protected abstract fun handleIntent(intent: Intent)

    /** Applies a message immediately from handleIntent, before the next intent is processed. */
    protected fun reduce(message: Message) {
        scope.ensureActive()
        mutableState.value = reducer(mutableState.value, message)
    }

    /** Runs asynchronous work and queues its result for the reducer. */
    protected fun launchEffect(block: suspend () -> Message): Job {
        return collectEffect(
            messages = flow { emit(value = block()) },
        )
    }

    /** Collects a stream, such as repository updates, until it completes or the store closes. */
    protected fun collectEffect(messages: Flow<Message>): Job {
        return scope.launch {
            messages.collect { message ->
                events.send(element = Event.Result(message = message))
            }
        }
    }

    final override fun close() {
        scope.cancel()
        events.cancel()
    }

    private sealed interface Event<out I, out M> {
        data class Input<I>(val intent: I) : Event<I, Nothing>

        data class Result<M>(val message: M) : Event<Nothing, M>
    }
}
