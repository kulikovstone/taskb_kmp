package com.mkulikov.taskb.feature.hello.screen.hello

internal sealed interface HelloMessage {
    data object Continue : HelloMessage

    data object Saved : HelloMessage

    data object SaveFailed : HelloMessage
}

internal fun reduceHello(state: HelloState, message: HelloMessage): HelloState {
    return when (message) {
        HelloMessage.Continue -> state.copy(isContinuing = true, error = null)
        HelloMessage.Saved -> state.copy(isContinuing = false, error = null, isCompleted = true)
        HelloMessage.SaveFailed -> state.copy(
            isContinuing = false,
            error = "Couldn't save settings. Please try again.",
        )
    }
}
