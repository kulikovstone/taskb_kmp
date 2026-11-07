package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme

/** Expands with its text; place in a scrolling form without a height limit. */
@Composable
fun TaskBTextArea(
    state: TextFieldState,
    description: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 4,
    enabled: Boolean = true,
) {
    val colors = TaskBTheme.colors
    val shape = RoundedCornerShape(size = 8.dp)
    val textStyle = TaskBTheme.typography.body.copy(
        color = colors.text,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    var focused by remember { mutableStateOf(value = false) }
    BasicTextField(
        state = state,
        enabled = enabled,
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines),
        textStyle = textStyle,
        cursorBrush = SolidColor(value = colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        modifier = modifier
            .onFocusChanged { focusState -> focused = focusState.isFocused }
            .clip(shape = shape)
            .background(color = colors.surface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.accent else colors.border,
                shape = shape,
            )
            .semantics { contentDescription = description },
        decorator = TextFieldDecorator { inner ->
            Box(modifier = Modifier.padding(all = 12.dp)) {
                if (state.text.isEmpty()) {
                    Text(text = placeholder, style = textStyle, color = colors.muted)
                }
                inner()
            }
        },
    )
}
