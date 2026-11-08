package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.button.TaskBActionButton
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Preview(name = "Action and selection controls", widthDp = 400)
@Composable
private fun TaskBButtonControlsPreview() {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            Column(
                modifier = Modifier.padding(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(space = 12.dp),
            ) {
                TaskBActionButton(label = "Add task", icon = TaskBIcons.Add, onClick = {})
                Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                    TaskBSelectionButton(
                        label = "Today",
                        selected = true,
                        onClick = {},
                        modifier = Modifier.weight(weight = 1f),
                    )
                    TaskBSelectionButton(
                        label = "Tomorrow",
                        selected = false,
                        onClick = {},
                        modifier = Modifier.weight(weight = 1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                    ColorSelector(label = "Blue", color = Palette.Blue.s400, selected = true, onClick = {})
                    ColorSelector(label = "Green", color = Palette.Green.s400, selected = false, onClick = {})
                }
            }
        }
    }
}

@Preview(name = "Selection rows", widthDp = 360)
@Composable
private fun TaskBSelectionRowsPreview() {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            Column {
                TaskBSelectionRow(
                    label = "Work",
                    selected = true,
                    onClick = {},
                    groupColor = Palette.Blue.s400,
                    trailingText = "8",
                    checkAfterLabel = true,
                )
                TaskBSelectionRow(
                    label = "No group",
                    selected = false,
                    onClick = {},
                    reserveGroupIconSpace = true,
                )
                TaskBSelectionRow(label = "Today", selected = true, onClick = {})
            }
        }
    }
}

@Preview(name = "Text area – empty and filled", widthDp = 400)
@Composable
private fun TaskBTextAreaPreview() {
    val empty = remember { TextFieldState() }
    val filled = remember {
        TextFieldState(initialText = "Review the release notes.\nCheck that the screenshots show the new design.")
    }
    TaskBTheme(darkTheme = true) {
        Surface(color = TaskBTheme.colors.surface) {
            Column(
                modifier = Modifier.padding(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(space = 12.dp),
            ) {
                TaskBTextArea(
                    state = empty,
                    description = "Description",
                    placeholder = "Add a description",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                TaskBTextArea(
                    state = filled,
                    description = "Description",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview(name = "Editor toolbar", widthDp = 840)
@Composable
private fun TaskBEditorTopAppBarPreview() {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            Column {
                TaskBEditorTopAppBar(title = "Edit task", onBack = {}, backEnabled = true) {
                    TaskBEditorButton(label = "Delete", icon = TaskBIcons.Delete, onClick = {}, destructive = true)
                    TaskBEditorButton(label = "Save changes", icon = TaskBIcons.Done, onClick = {})
                }
                Divider(color = TaskBTheme.colors.border)
                TaskBEditorTopAppBar(title = "New task", onBack = {}, backEnabled = true) {
                    TaskBEditorButton(label = "Create task", icon = TaskBIcons.Add, onClick = {}, enabled = false)
                }
            }
        }
    }
}

@Preview(name = "Compact toolbars", widthDp = 400)
@Composable
private fun TaskBTopAppBarPreview() {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            Column {
                TaskBTopAppBar(title = "Settings", onBack = {})
                Divider(color = TaskBTheme.colors.border)
                TaskBTopAppBar(
                    title = "Tasks",
                    onBack = {},
                    titleSpacing = 4.dp,
                    subtitle = {
                        Text(text = "4 tasks", style = TaskBTheme.typography.small, color = TaskBTheme.colors.muted)
                    },
                ) {
                    IconButton(onClick = {}) {
                        Icon(imageVector = TaskBIcons.Add, contentDescription = "Add task")
                    }
                }
            }
        }
    }
}

@Preview(name = "Loading and retry states", widthDp = 360)
@Composable
private fun TaskBFeedbackPreview() {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            Column {
                TaskBLoading(modifier = Modifier.height(height = 104.dp))
                Divider(color = TaskBTheme.colors.border)
                TaskBLoadError(message = "Couldn't load tasks.", onRetry = {})
            }
        }
    }
}
