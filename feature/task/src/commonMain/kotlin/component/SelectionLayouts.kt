package com.mkulikov.taskb.feature.task.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.dialog.TaskBDialog
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun SelectionScreen(title: String, onClose: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TaskBTopAppBar(title = title, onBack = onClose, titleSpacing = 4.dp)
        content(Modifier.weight(weight = 1f).padding(start = 24.dp, end = 24.dp, top = 24.dp))
    }
}

@Composable
internal fun SelectionDialog(
    title: String,
    onClose: () -> Unit,
    availableHeight: Dp,
    preferredHeight: Dp,
    content: @Composable (Modifier) -> Unit,
) {
    TaskBDialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(size = 10.dp), color = TaskBTheme.colors.surface, elevation = 4.dp) {
            Column(
                modifier = Modifier
                    .width(width = 480.dp)
                    .height(
                        height = minOf(
                            a = preferredHeight,
                            b = (availableHeight - 48.dp).coerceAtLeast(minimumValue = 160.dp),
                        )
                    )
                    .padding(all = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(height = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = TaskBTheme.typography.title,
                        color = TaskBTheme.colors.heading,
                        modifier = Modifier.weight(weight = 1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = TaskBIcons.Clear,
                            contentDescription = "Close selection",
                            tint = TaskBTheme.colors.muted,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(height = 16.dp))
                content(Modifier.weight(weight = 1f))
            }
        }
    }
}
