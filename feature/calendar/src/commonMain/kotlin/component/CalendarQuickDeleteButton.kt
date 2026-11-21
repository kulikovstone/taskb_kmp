package com.mkulikov.taskb.feature.calendar.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarIntent
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

internal fun Task.quickDeleteIntent(): CalendarIntent.PrepareQuickDelete? {
    val occurrence = occurrenceId?.takeIf { canQuickDelete } ?: return null
    return CalendarIntent.PrepareQuickDelete(id = id, occurrenceId = occurrence)
}

@Composable
internal fun CalendarQuickDeleteButton(
    taskName: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(value = LocalRippleConfiguration provides null) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(width = 44.dp, height = 30.dp),
        ) {
            Icon(
                imageVector = TaskBIcons.Delete,
                contentDescription = "Delete occurrence of $taskName",
                tint = TaskBTheme.colors.danger,
                modifier = Modifier.size(size = 20.dp),
            )
        }
    }
}
