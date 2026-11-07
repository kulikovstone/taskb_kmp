package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
fun TaskBTextField(
    state: TextFieldState,
    description: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    search: Boolean = false,
    enabled: Boolean = true,
    autoFocus: Boolean = false,
    showClear: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
    inputTransformation: InputTransformation? = null,
) {
    val colors = TaskBTheme.colors
    TaskBTextFieldContainer(
        value = state.text.toString(),
        onClear = state::clearText,
        description = description,
        modifier = modifier,
        placeholder = placeholder,
        search = search,
        enabled = enabled,
        autoFocus = autoFocus,
        showClear = showClear,
    ) { fieldModifier, decoration ->
        BasicTextField(
            state = state,
            inputTransformation = inputTransformation,
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = TaskBTheme.typography.body.copy(color = colors.text),
            cursorBrush = SolidColor(value = colors.accent),
            keyboardOptions = KeyboardOptions(
                imeAction = if (search) ImeAction.Search else ImeAction.Done,
                keyboardType = keyboardType,
            ),
            onKeyboardAction = { onDone() },
            modifier = fieldModifier,
            decorator = TextFieldDecorator { inner -> decoration(inner) },
        )
    }
}

@Composable
private fun TaskBTextFieldContainer(
    value: String,
    onClear: () -> Unit,
    description: String,
    modifier: Modifier,
    placeholder: String,
    search: Boolean,
    enabled: Boolean,
    autoFocus: Boolean,
    showClear: Boolean,
    field: @Composable (Modifier, @Composable (@Composable () -> Unit) -> Unit) -> Unit,
) {
    val colors = TaskBTheme.colors
    var focused by remember { mutableStateOf(value = false) }
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = modifier
            .height(height = 44.dp)
            .onFocusChanged { focusState ->
                focused = focusState.hasFocus
            }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        field(
            Modifier.weight(weight = 1f)
                .height(height = 44.dp)
                .focusRequester(focusRequester = focusRequester)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.accent else colors.border,
                    shape = RoundedCornerShape(size = 8.dp),
                )
                .semantics { contentDescription = description }
        ) { inner ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (search) {
                    Icon(
                        imageVector = TaskBIcons.Search,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(size = 18.dp),
                    )
                    Spacer(modifier = Modifier.width(width = 8.dp))
                }
                Box(modifier = Modifier.weight(weight = 1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty())
                        Text(text = placeholder, color = colors.muted, style = TaskBTheme.typography.body)
                    inner()
                }
            }
        }
        if (showClear && focused && value.isNotEmpty()) {
            Spacer(modifier = Modifier.width(width = 8.dp))
            Box(
                modifier = Modifier
                    .size(size = 32.dp)
                    .clickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                    ) {
                        onClear()
                        focusRequester.requestFocus()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TaskBIcons.Cancel,
                    contentDescription = "Clear ${description.lowercase()}",
                    tint = colors.clearIcon,
                    modifier = Modifier.size(size = 24.dp),
                )
            }
        }
    }
    LaunchedEffect(key1 = autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
}

@Preview(name = "Light", group = "TaskBTextField")
@Composable
private fun TaskBTextFieldLightPreview() {
    TaskBTextFieldPreview(darkTheme = false)
}

@Preview(name = "Dark", group = "TaskBTextField")
@Composable
private fun TaskBTextFieldDarkPreview() {
    TaskBTextFieldPreview(darkTheme = true)
}

@Composable
private fun TaskBTextFieldPreview(darkTheme: Boolean) {
    TaskBTheme(darkTheme = darkTheme) {
        Surface(color = TaskBTheme.colors.surface) {
            Column(
                modifier = Modifier.width(width = 400.dp).padding(all = 24.dp),
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                TaskBTextField(
                    state = rememberTextFieldState(),
                    description = "Empty task name",
                    placeholder = "Enter task name",
                )
                TaskBTextField(
                    state = rememberTextFieldState(initialText = "Plan the next release"),
                    description = "Task name",
                )
                TaskBTextField(
                    state = rememberTextFieldState(initialText = "Write release notes"),
                    description = "Focused task name",
                    autoFocus = true,
                )
                TaskBTextField(
                    state = rememberTextFieldState(initialText = "release"),
                    description = "Search tasks",
                    placeholder = "Search tasks",
                    search = true,
                )
                TaskBTextField(
                    state = rememberTextFieldState(initialText = "Archived task"),
                    description = "Disabled task name",
                    enabled = false,
                )
            }
        }
    }
}
