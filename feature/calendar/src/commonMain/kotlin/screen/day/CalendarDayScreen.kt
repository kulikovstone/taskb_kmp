package com.mkulikov.taskb.feature.calendar.screen.day

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate

/** Navigation placeholder until the separate day screen is implemented. */
@Composable
internal fun CalendarDayScreen(date: LocalDate, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TaskBTopAppBar(date.toString(), onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Day view is coming soon.", color = TaskBTheme.colors.muted)
        }
    }
}
