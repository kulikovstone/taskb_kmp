package com.mkulikov.taskb.ui.input

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then

fun digitsOnlyInputTransformation(maxLength: Int): InputTransformation =
    InputTransformation.maxLength(maxLength).then {
        if (!asCharSequence().all(Char::isDigit)) revertAllChanges()
    }
