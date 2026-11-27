package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.startDateTime
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

internal val calendarEventMarkerWidth = 3.dp

@Composable
internal fun CalendarEventRow(
    event: Event,
    group: Group?,
    onOpenEvent: (Int) -> Unit,
    onFilterByGroup: ((Int) -> Unit)?,
    onFilterByDate: ((LocalDate) -> Unit)?,
    modifier: Modifier,
    dense: Boolean,
    enabled: Boolean,
    dragging: Boolean,
    date: LocalDate?,
    now: Instant,
    contentPadding: PaddingValues,
) {
    val hasTime = event.startDateTime != null
    val colors = TaskBTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val subdued = event.isPastCalendarEntry(date = date.takeIf { dense }, now = now)
    val titleColor = if (subdued) colors.completedText else colors.text
    val timeColor = if (subdued) colors.completedText else colors.taskTime
    val secondaryColor = if (subdued) colors.completedText else colors.muted
    val lineHeight = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)
    Row(
        modifier = modifier
            .graphicsLayer { alpha = if (dragging) 0.25f else 1f }
            .fillMaxWidth()
            .height(intrinsicSize = IntrinsicSize.Min)
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .hoverable(interactionSource = interaction, enabled = enabled)
            .indication(interactionSource = interaction, indication = LocalIndication.current)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                onOpenEvent(event.id)
            }
            .padding(paddingValues = contentPadding)
            .padding(top = 4.dp, bottom = 4.dp, end = if (dense) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = if (dense) 4.dp else 10.dp),
    ) {
        if (group != null)
            Box(
                modifier = Modifier
                    .width(width = calendarEventMarkerWidth)
                    .fillMaxHeight()
                    .then(other = if (dense) Modifier else Modifier.padding(vertical = 3.dp))
                    .background(color = group.color.color)
                    .testTag(tag = "calendar-event-marker-${event.key}")
            )
        if (dense) {
            val label = calendarMonthEventText(task = event, date = date)
            val text = buildAnnotatedString {
                append(text = label)
                val clockStart = if (!hasTime) {
                    null
                } else {
                    when {
                        !event.isMultiDayCalendarEvent || date == null || date == event.dueDate -> 0
                        date == event.calendarLastDate -> label.length - 5
                        else -> null
                    }
                }
                if (clockStart != null)
                    addStyle(style = SpanStyle(color = timeColor), start = clockStart, end = clockStart + 5)
            }
            Text(
                text = text,
                modifier = Modifier.weight(weight = 1f),
                color = titleColor,
                style = calendarTaskTitleStyle(dense = true),
                textDecoration = TextDecoration.None,
                maxLines = if (hasTime && event.isMultiDayCalendarEvent) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column(
                modifier = Modifier.weight(weight = 1f),
                verticalArrangement = Arrangement.spacedBy(space = 2.dp),
            ) {
                if (hasTime) {
                    Text(
                        text = calendarEventTimeRange(
                            task = event,
                            timeColor = timeColor,
                            onFilterByDate = if (enabled) onFilterByDate else null,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag(tag = "calendar-event-time-${event.key}"),
                        color = secondaryColor,
                        style = TaskBTheme.typography.label.copy(lineHeightStyle = lineHeight),
                        textDecoration = TextDecoration.None,
                    )
                }
                Text(
                    text = event.name,
                    color = titleColor,
                    style = calendarTaskTitleStyle(dense = false),
                    textDecoration = TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.description.isNotBlank())
                    Text(
                        text = event.description,
                        color = colors.muted,
                        style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeight),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                if (group != null) {
                    CalendarGroupLabel(
                        group = group,
                        enabled = enabled,
                        onFilterByGroup = onFilterByGroup,
                        style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeight),
                    )
                }
            }
        }
    }
}
