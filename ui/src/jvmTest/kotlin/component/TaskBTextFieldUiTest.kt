package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import com.mkulikov.taskb.ui.input.digitsOnlyInputTransformation
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TaskBTextFieldUiTest {
    @Test
    fun `numeric input rejects invalid paste and length without moving the cursor`() {
        for (maxLength in listOf(2, 4, 7)) {
            val state = TextFieldState(initialText = "1")
            val transformation = digitsOnlyInputTransformation(maxLength = maxLength)
            runSkikoComposeUiTest {
                setContent {
                    TaskBTheme(darkTheme = false) {
                        TaskBTextField(state = state, description = "Number", inputTransformation = transformation)
                    }
                }
                val field = onNodeWithContentDescription(label = "Number")
                field.performTextInputSelection(selection = TextRange(index = 1))
                field.performTextInput(text = "2")
                field.assertTextEquals("12")
                field.performTextInput(text = "x")
                field.assertTextEquals("12")
                field.assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.TextSelectionRange,
                        expectedValue = TextRange(index = 2),
                    )
                )
                field.performTextInput(text = "9".repeat(n = maxLength))
                field.assertTextEquals("12")
                field.assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.TextSelectionRange,
                        expectedValue = TextRange(index = 2),
                    )
                )
                field.performTextReplacement(text = "")
                field.assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.EditableText,
                        expectedValue = AnnotatedString(text = ""),
                    )
                )
                field.performTextInput(text = "9".repeat(n = maxLength))
                field.assertTextEquals("9".repeat(n = maxLength))
                field.performTextReplacement(text = "1a")
                field.assertTextEquals("9".repeat(n = maxLength))
            }
        }
    }
}
