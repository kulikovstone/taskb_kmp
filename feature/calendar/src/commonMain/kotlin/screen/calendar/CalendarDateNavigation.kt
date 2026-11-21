package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun CalendarDateNavigation(onIntent: (CalendarIntent) -> Unit) {
    val colors = TaskBTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(
                first = TaskBIcons.DoubleChevronLeft,
                second = "Previous year",
                third = CalendarIntent.PreviousYear,
            ),
            Triple(
                first = TaskBIcons.ChevronLeft,
                second = "Previous month",
                third = CalendarIntent.PreviousMonth,
            ),
            Triple(
                first = TaskBIcons.ChevronRight,
                second = "Next month",
                third = CalendarIntent.NextMonth,
            ),
            Triple(
                first = TaskBIcons.DoubleChevronRight,
                second = "Next year",
                third = CalendarIntent.NextYear,
            ),
        ).forEach { (icon, description, intent) ->
            OutlinedButton(
                onClick = { onIntent(intent) },
                modifier = Modifier.size(size = 40.dp),
                shape = RoundedCornerShape(size = 8.dp),
                border = BorderStroke(width = 1.dp, color = colors.border),
                contentPadding = PaddingValues(all = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = colors.secondary,
                    modifier = Modifier.size(size = 18.dp),
                )
            }
        }
        OutlinedButton(
            onClick = { onIntent(CalendarIntent.Today) },
            modifier = Modifier
                .width(width = 72.dp)
                .height(height = 40.dp)
                .testTag(tag = "calendar-today"),
            shape = RoundedCornerShape(size = 8.dp),
            border = BorderStroke(width = 1.dp, color = colors.border),
            contentPadding = PaddingValues(all = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface),
        ) {
            Text(text = "Today", style = TaskBTheme.typography.button, color = colors.accent)
        }
    }
}
