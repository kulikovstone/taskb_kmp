package com.mkulikov.taskb.feature.home.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.button.TaskBButton
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun HomeScreen(
    appVersion: String,
    onTasksClick: () -> Unit,
    onGroupsClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(alignment = Alignment.Center).padding(all = 24.dp),
            verticalArrangement = Arrangement.spacedBy(space = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TaskBButton(label = "Groups", onClick = onGroupsClick)
            TaskBButton(label = "Tasks", onClick = onTasksClick)
            TaskBButton(label = "Calendar", onClick = onCalendarClick)
            TaskBButton(label = "Settings", onClick = onSettingsClick)
        }
        Text(
            text = "Version $appVersion",
            modifier = Modifier.align(alignment = Alignment.BottomCenter).padding(all = 16.dp),
            style = MaterialTheme.typography.caption,
            color = TaskBTheme.colors.text.copy(alpha = ContentAlpha.medium),
        )
    }
}

@Preview(name = "Home", widthDp = 800, heightDp = 600)
@Preview(name = "Home – compact", widthDp = 400, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            HomeScreen(
                appVersion = "Preview",
                onTasksClick = {},
                onGroupsClick = {},
                onCalendarClick = {},
            )
        }
    }
}
