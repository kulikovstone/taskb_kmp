package com.mkulikov.taskb.ui.input

import androidx.compose.foundation.text.input.TextFieldState

/** Keep the cursor and IME composition when a programmatic update has the same text. */
fun TextFieldState.withTextIfChanged(text: String): TextFieldState {
    return if (this.text.toString() == text) this else TextFieldState(initialText = text)
}
