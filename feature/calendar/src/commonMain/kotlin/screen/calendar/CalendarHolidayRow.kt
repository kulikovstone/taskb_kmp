package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate

internal val calendarHolidayMarkerWidth = calendarEventMarkerWidth * 1.5f

/** Public holidays are informational: no editor, completion control, or drag gesture. */
@Composable
internal fun CalendarHolidayRow(holiday: PublicHoliday, modifier: Modifier = Modifier, dense: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp), today: LocalDate) {
    val colors = TaskBTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)
        .semantics { contentDescription = "Public holiday: ${holiday.name}" }
        .padding(contentPadding).padding(top = 4.dp, bottom = 4.dp, end = if (dense) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 10.dp)) {
        Box(Modifier.width(calendarHolidayMarkerWidth).fillMaxHeight()
            .then(if (dense) Modifier else Modifier.padding(vertical = 3.dp))
            .background(Palette.Green.s900).testTag("calendar-holiday-marker-${holiday.id}"))
        Text(holiday.name, modifier = Modifier.weight(1f).testTag("calendar-holiday-title-${holiday.id}"),
            color = if (dense && holiday.date < today) colors.completedText else colors.text, style = calendarTaskTitleStyle(dense),
            maxLines = if (dense) 1 else 2, overflow = TextOverflow.Ellipsis)
    }
}
