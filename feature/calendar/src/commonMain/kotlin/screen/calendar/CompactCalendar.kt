package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.yearMonth

private val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val compactLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Composable
internal fun CompactCalendar(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(color = TaskBTheme.colors.surface)) {
        TaskBTopAppBar(
            title = calendarMonthLabel.format(value = state.month),
            onBack = onBack,
            modifier = Modifier.height(height = 60.dp),
            titleSpacing = 0.dp,
            contentPadding = PaddingValues(start = 8.dp, top = 12.dp, end = 8.dp),
            contentAlpha = 1f,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(state = rememberScrollState())
                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
        ) {
            CalendarDateNavigation(onIntent = onIntent)
        }
        when {
            state.isLoading -> TaskBLoading(modifier = Modifier.weight(weight = 1f))
            state.loadError != null -> TaskBLoadError(
                message = state.loadError,
                onRetry = { onIntent(CalendarIntent.RetryLoad) },
            )
            else -> CompactCalendarGrid(
                state = state,
                onOpenDay = onOpenDay,
                modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompactCalendarGrid(
    state: CalendarState,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TaskBTheme.colors
    val itemsByDate = state.monthItemsByDate
    val groupsById = state.groups.associateBy(keySelector = Group::id)
    val columnSpacing = Arrangement.spacedBy(space = 1.dp)
    Column(
        modifier = modifier
            .testTag(tag = "calendar-grid")
            .background(color = colors.border)
            .padding(all = 1.dp),
        verticalArrangement = Arrangement.spacedBy(space = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height = 34.dp).background(color = colors.subtle),
            horizontalArrangement = columnSpacing,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (name in weekdayNames) {
                Box(modifier = Modifier.weight(weight = 1f), contentAlignment = Alignment.Center) {
                    Text(text = name, style = TaskBTheme.typography.label, color = colors.secondary, maxLines = 1)
                }
            }
        }
        for (week in state.days.chunked(size = 7)) {
            Row(
                modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
                horizontalArrangement = columnSpacing,
            ) {
                for (date in week) {
                    key(date) {
                        CompactCalendarCell(
                            date = date,
                            items = itemsByDate[date].orEmpty(),
                            groupsById = groupsById,
                            state = state,
                            onClick = { onOpenDay(date) },
                            modifier = Modifier.weight(weight = 1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCalendarCell(
    date: LocalDate,
    items: List<CalendarItem>,
    groupsById: Map<Int, Group>,
    state: CalendarState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TaskBTheme.colors
    val outsideMonth = date.yearMonth != state.month
    val isToday = date == state.today
    val isSelected = date == state.selectedDate
    val background = when {
        isSelected -> colors.calendarSelectedDay
        outsideMonth || date.dayOfWeek.isoDayNumber >= 6 -> colors.subtle
        else -> colors.surface
    }
    Column(
        modifier = modifier
            .clipToBounds()
            .background(color = background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Show tasks and events for $date"
                selected = isSelected
            }
            .testTag(tag = "calendar-day-$date")
            .padding(all = 1.dp),
        verticalArrangement = Arrangement.spacedBy(space = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 14.dp)
                .then(
                    other = if (isToday) {
                        Modifier.background(color = colors.actionFill, shape = RoundedCornerShape(size = 4.dp))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.day.toString(),
                color = when {
                    isToday -> colors.onAction
                    outsideMonth -> colors.muted
                    else -> colors.text
                },
                style = TaskBTheme.typography.label.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    lineHeightStyle = compactLineHeightStyle,
                ),
                maxLines = 1,
            )
        }
        CompactCalendarEntries(
            items = items,
            groupsById = groupsById,
            date = date,
            state = state,
            modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactCalendarEntries(
    items: List<CalendarItem>,
    groupsById: Map<Int, Group>,
    date: LocalDate,
    state: CalendarState,
    modifier: Modifier = Modifier,
) {
    val colors = TaskBTheme.colors
    val style = TaskBTheme.typography.small.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        lineHeightStyle = compactLineHeightStyle,
    )
    SubcomposeLayout(modifier = modifier) { constraints ->
        val rowHeight = 14.sp.roundToPx().coerceAtLeast(minimumValue = 1)
        val gap = 1.dp.roundToPx()
        val rowCount = (constraints.maxHeight + gap) / (rowHeight + gap)
        val rows = if (rowCount == 0 || constraints.maxWidth == 0) {
            emptyList()
        } else {
            val shown = if (items.size > rowCount) rowCount - 1 else items.size
            val rowConstraints = Constraints(
                maxWidth = constraints.maxWidth,
                minHeight = rowHeight,
                maxHeight = rowHeight,
            )
            buildList {
                for (item in items.take(n = shown)) {
                    add(element = subcompose(slotId = item.key) {
                        val event = (item as? CalendarItem.EventItem)?.event
                        val pastEvent = event?.isPastCalendarEntry(date = date, now = state.now) == true
                        val inactive = item.isCompleted || pastEvent
                        val tint = when {
                            inactive -> colors.calendarInactiveEntryMarker
                            item.isHoliday -> colors.success
                            else -> groupsById[item.groupId]?.color?.color ?: colors.muted
                        }
                        Row(
                            modifier = Modifier.clipToBounds(),
                            horizontalArrangement = Arrangement.spacedBy(space = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(width = calendarEventMarkerWidth)
                                    .fillMaxHeight()
                                    .background(color = tint),
                            )
                            Text(
                                text = event?.let { value ->
                                    calendarMonthEventText(task = value, date = date, endTimeFirst = true)
                                } ?: item.name,
                                style = style,
                                color = if (inactive) colors.calendarInactiveEntryText else colors.text,
                                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }.single().measure(constraints = rowConstraints))
                }
                if (shown < items.size) {
                    add(element = subcompose(slotId = "overflow") {
                        Text(
                            text = "+${items.size - shown}",
                            style = style.copy(fontSize = 10.sp),
                            color = colors.accent,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }.single().measure(constraints = rowConstraints))
                }
            }
        }
        layout(width = constraints.maxWidth, height = constraints.maxHeight) {
            rows.forEachIndexed { index, row ->
                row.placeRelative(x = 0, y = index * (rowHeight + gap))
            }
        }
    }
}

@Preview(name = "Compact calendar", widthDp = 400, heightDp = 800)
@Composable
private fun CompactCalendarPreview() {
    TaskBTheme(darkTheme = false) {
        CompactCalendar(state = timedCalendarPreviewState(), onIntent = {}, onBack = {}, onOpenDay = {})
    }
}
