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
    Column(Modifier.fillMaxSize()) {
        TaskBTopAppBar(title, onClose, titleSpacing = 4.dp)
        content(Modifier.weight(1f).padding(start = 24.dp, end = 24.dp, top = 24.dp))
    }
}

@Composable
internal fun SelectionDialog(title: String, onClose: () -> Unit, availableHeight: Dp, preferredHeight: Dp, content: @Composable (Modifier) -> Unit) {
    TaskBDialog(onClose) {
        Surface(shape = RoundedCornerShape(10.dp), color = TaskBTheme.colors.surface, elevation = 4.dp) {
            Column(Modifier.width(480.dp).height(minOf(preferredHeight, (availableHeight - 48.dp).coerceAtLeast(160.dp))).padding(24.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = TaskBTheme.typography.title, color = TaskBTheme.colors.heading, modifier = Modifier.weight(1f))
                    IconButton(onClose) { Icon(TaskBIcons.Clear, "Close selection", tint = TaskBTheme.colors.muted) }
                }
                Spacer(Modifier.height(16.dp))
                content(Modifier.weight(1f))
            }
        }
    }
}
