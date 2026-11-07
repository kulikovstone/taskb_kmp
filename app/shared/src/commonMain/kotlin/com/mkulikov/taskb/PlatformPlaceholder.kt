package com.mkulikov.taskb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun PlatformPlaceholder() {
    TaskBTheme {
        Surface(color = TaskBTheme.colors.surface) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(all = 24.dp),
                verticalArrangement = Arrangement.spacedBy(space = 16.dp, alignment = Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "TaskB", style = TaskBTheme.typography.display, color = TaskBTheme.colors.heading)
                Text(text = "Support for this platform is planned.", color = TaskBTheme.colors.muted)
            }
        }
    }
}
