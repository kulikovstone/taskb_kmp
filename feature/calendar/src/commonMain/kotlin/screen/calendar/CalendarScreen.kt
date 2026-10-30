package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun CalendarScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        Column(Modifier.fillMaxSize()) {
            TaskBTopAppBar(title = "Calendar", onBack = onBack)
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Calendar is coming soon.", color = TaskBTheme.colors.text)
            }
        }
    }
}

@Preview(name = "Calendar", widthDp = 800, heightDp = 600)
@Composable
private fun CalendarScreenPreview() {
    TaskBTheme(darkTheme = false) {
        CalendarScreen(onBack = {})
    }
}
