package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.math.ceil
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.yearMonth

@Composable
internal fun CalendarMonthGrid(
    state: CalendarState,
    drag: CalendarDragState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenTask: (CalendarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TaskBTheme.colors
    val tasks = state.monthItemsByDate
    val weeks = state.days.chunked(size = 7)
    val weather = state.weatherByDate
    Column(
        modifier = modifier
            .calendarMonthGestures(onIntent = onIntent)
            .testTag(tag = "calendar-grid")
            .clipToBounds()
            .border(width = 1.dp, color = colors.border)
            .padding(all = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(height = 40.dp)) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").forEach { name
                    ->
                    Text(
                        text = name,
                        modifier = Modifier.weight(weight = 1f).padding(horizontal = 12.dp),
                        color = colors.muted,
                        style = TaskBTheme.typography.button,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Divider(modifier = Modifier.align(alignment = Alignment.BottomCenter), color = colors.border)
        }
        weeks.forEachIndexed { weekIndex, week ->
            Row(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                week.forEachIndexed { dayIndex, date ->
                    key(date) {
                        CalendarDayCell(
                            date = date,
                            tasks = tasks[date].orEmpty(),
                            state = state,
                            drag = drag,
                            onIntent = onIntent,
                            onOpenTask = onOpenTask,
                            weather = weather[date],
                            endBorder = dayIndex < 6,
                            bottomBorder = weekIndex < weeks.lastIndex,
                            modifier = Modifier.weight(weight = 1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    tasks: List<CalendarItem>,
    state: CalendarState,
    drag: CalendarDragState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenTask: (CalendarItem) -> Unit,
    weather: DailyWeather?,
    endBorder: Boolean,
    bottomBorder: Boolean,
    modifier: Modifier,
) {
    val colors = TaskBTheme.colors
    val outsideMonth = date.yearMonth != state.month
    val isToday = date == state.today
    val hovered = drag.hoveredDay == date
    val isSelected = date == state.selectedDate
    DisposableEffect(key1 = drag, key2 = date) { onDispose { drag.targets.remove(key = date) } }
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates -> drag.targets[date] = coordinates.boundsInRoot() }
            .background(
                color = when {
                    hovered -> colors.selected
                    isSelected -> colors.calendarSelectedDay
                    outsideMonth || date.dayOfWeek.isoDayNumber >= 6 -> colors.subtle
                    else -> colors.surface
                }
            )
            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                onIntent(CalendarIntent.SelectDay(date = date))
            }
            .semantics {
                contentDescription = "Show tasks for $date"
                selected = hovered || isSelected
            }
            .testTag(tag = "calendar-day-$date")
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (endBorder) 1.dp else 0.dp, bottom = if (bottomBorder) 1.dp else 0.dp)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(height = 20.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(size = 20.dp)
                        .then(
                            other = if (isToday) {
                                Modifier.background(
                                    color = colors.actionFill,
                                    shape = RoundedCornerShape(size = 4.dp),
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(interactionSource = null, indication = null, role = Role.Button) {
                            onIntent(CalendarIntent.SelectDay(date = date))
                        }
                        .semantics { contentDescription = "Show tasks for $date" }
                        .testTag(tag = "calendar-day-number-$date"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.day.toString(),
                        color = if (isToday) colors.onAction else if (outsideMonth) colors.muted else colors.text,
                        style = TaskBTheme.typography.button,
                    )
                }
                if (weather != null)
                    CalendarWeatherSummary(weather = weather, modifier = Modifier.weight(weight = 1f, fill = false))
                if (outsideMonth)
                    Text(
                        text = calendarMonthAbbreviation.format(value = date),
                        color = colors.muted,
                        style = TaskBTheme.typography.button,
                        maxLines = 1,
                    )
            }
            CalendarDayTasks(
                date = date,
                tasks = tasks,
                state = state,
                drag = drag,
                onIntent = onIntent,
                onOpenTask = onOpenTask,
                onSelectDay = { onIntent(CalendarIntent.SelectDay(date = date)) },
                modifier = Modifier.weight(weight = 1f).fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
        if (endBorder)
            Box(
                modifier = Modifier
                    .align(alignment = Alignment.CenterEnd)
                    .width(width = 1.dp)
                    .fillMaxHeight()
                    .background(color = colors.border)
            )
        if (bottomBorder)
            Divider(modifier = Modifier.align(alignment = Alignment.BottomCenter), color = colors.border)
    }
}

/** Учитываем высоту перенесённых названий и ссылки на остальные задачи, чтобы не обрезать строки. */
@Composable
private fun CalendarDayTasks(
    date: LocalDate,
    tasks: List<CalendarItem>,
    state: CalendarState,
    drag: CalendarDragState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenTask: (CalendarItem) -> Unit,
    onSelectDay: () -> Unit,
    modifier: Modifier,
) {
    val colors = TaskBTheme.colors
    val titleStyle = calendarTaskTitleStyle(dense = true)
    val moreStyle = TaskBTheme.typography.body.copy(
        lineHeight = 18.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    val textMeasurer = rememberTextMeasurer()
    SubcomposeLayout(modifier = modifier) { constraints ->
        val rows = buildList<Placeable> {
            var measuredHeight = 0
            for (task in tasks) {
                val timed = task.startDateTime != null
                val group = state.groups.find { group -> group.id == task.groupId }
                val taskInsets = when {
                    task.isHoliday -> calendarHolidayMarkerWidth.roundToPx() + 4.dp.roundToPx()
                    !task.isEvent -> calendarTaskPadding.roundToPx() * 2 +
                        calendarTaskCheckboxSize.roundToPx() +
                        calendarTaskTitleGap.roundToPx()
                    group != null -> calendarEventMarkerWidth.roundToPx() + 4.dp.roundToPx()
                    else -> 0
                }
                val titleMaxWidth = (constraints.maxWidth - taskInsets).coerceAtLeast(minimumValue = 0)
                val label = if (timed) calendarMonthItemText(item = task, date = date) else task.name
                val maxLines = if (task.isHoliday || timed && task.isMultiDayCalendarEvent) 1 else 2
                val titleLayout = textMeasurer.measure(
                    text = label,
                    style = titleStyle,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = titleMaxWidth),
                )
                val titleWidth = if (titleLayout.multiParagraph.didExceedMaxLines) {
                    titleMaxWidth
                } else {
                    ceil(
                            x = (0 until titleLayout.lineCount).maxOf { lineIndex ->
                                titleLayout.getLineRight(lineIndex = lineIndex) -
                                    titleLayout.getLineLeft(lineIndex = lineIndex)
                            }
                        )
                        .toInt()
                        .coerceIn(minimumValue = 0, maximumValue = titleMaxWidth)
                }
                val taskWidth = (taskInsets + titleWidth).coerceAtMost(maximumValue = constraints.maxWidth)
                val row = subcompose(slotId = task.key) {
                        val enabled = !task.isHoliday &&
                            (task.isEvent || task.id !in state.pendingTaskUpdates) &&
                            state.pendingMove == null
                        CalendarTaskRow(
                            task = task,
                            group = group,
                            onOpenTask = { onOpenTask(task) },
                            onToggle = { taskId ->
                                onIntent(
                                    CalendarIntent.ToggleCompleted(
                                        id = taskId,
                                        occurrenceId = task.occurrenceId,
                                    )
                                )
                            },
                            modifier = Modifier
                                .testTag(
                                        tag = if (task.isEvent || task.isHoliday) {
                                            "calendar-${task.key}"
                                        } else {
                                            "calendar-task-${task.key}"
                                        }
                                    )
                                .calendarTaskDrag(
                                    task = task,
                                    dragState = drag,
                                    enabled = enabled,
                                    dense = true,
                                    sourceDate = date,
                                ) { id, target ->
                                    onIntent(task.moveTo(date = target))
                                },
                            dense = true,
                            enabled = enabled,
                            dragging = drag.item?.key == task.key,
                            date = date,
                            now = state.now,
                            today = state.today,
                        )
                    }
                    .single()
                    .measure(constraints = Constraints.fixedWidth(width = taskWidth))
                add(element = row)
                measuredHeight += row.height
                if (measuredHeight > constraints.maxHeight) break
            }
        }
        val moreHeight = textMeasurer
            .measure(
                text = "+${tasks.size} more",
                style = moreStyle,
                maxLines = 1,
                constraints = Constraints(
                    maxWidth = (constraints.maxWidth - calendarTaskTitleInset.roundToPx()).coerceAtLeast(
                        minimumValue = 0
                    )
                ),
            )
            .size
            .height
        var visible = rows.size
        var rowsHeight = rows.sumOf(selector = Placeable::height)
        while (visible > 0 && rowsHeight + (if (tasks.size > visible) moreHeight else 0) > constraints.maxHeight) {
            visible--
            rowsHeight -= rows[visible].height
        }
        val more = if (tasks.size > visible) {
            subcompose(slotId = "more") {
                    Text(
                        text = "+${tasks.size - visible} more",
                        color = colors.accent,
                        style = moreStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = calendarTaskTitleInset)
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Button,
                                onClick = onSelectDay,
                            ),
                    )
                }
                .single()
                .measure(
                    constraints = Constraints(
                        maxWidth = constraints.maxWidth,
                        maxHeight = (constraints.maxHeight - rowsHeight).coerceAtLeast(minimumValue = 0),
                    )
                )
        } else {
            null
        }
        layout(width = constraints.maxWidth, height = constraints.maxHeight) {
            var y = 0
            rows.take(n = visible).forEach { row ->
                row.placeRelative(x = 0, y = y)
                y += row.height
            }
            more?.placeRelative(x = 0, y = y)
        }
    }
}
