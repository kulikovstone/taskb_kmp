package com.mkulikov.taskb.ui.input

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then

fun digitsOnlyInputTransformation(maxLength: Int): InputTransformation {
    return InputTransformation
        .maxLength(maxLength = maxLength)
        .then {
            if (!asCharSequence().all(predicate = Char::isDigit)) revertAllChanges()
        }
}
