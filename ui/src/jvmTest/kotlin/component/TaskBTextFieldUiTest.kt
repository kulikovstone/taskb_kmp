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
            val state = TextFieldState("1")
            val transformation = digitsOnlyInputTransformation(maxLength)
            runSkikoComposeUiTest {
                setContent { TaskBTheme(false) { TaskBTextField(state, "Number", inputTransformation = transformation) } }
                val field = onNodeWithContentDescription("Number")
                field.performTextInputSelection(TextRange(1))
                field.performTextInput("2")
                field.assertTextEquals("12")
                field.performTextInput("x")
                field.assertTextEquals("12")
                field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(2)))
                field.performTextInput("9".repeat(maxLength))
                field.assertTextEquals("12")
                field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(2)))
                field.performTextReplacement("")
                field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                field.performTextInput("9".repeat(maxLength))
                field.assertTextEquals("9".repeat(maxLength))
                field.performTextReplacement("1a")
                field.assertTextEquals("9".repeat(maxLength))
            }
        }
    }
}
