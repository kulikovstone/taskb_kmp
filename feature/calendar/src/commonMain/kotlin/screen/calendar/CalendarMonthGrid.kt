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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.startDateTime
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.yearMonth
import kotlin.math.ceil

@Composable
internal fun CalendarMonthGrid(state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (Task) -> Unit, modifier: Modifier = Modifier) {
    val colors = TaskBTheme.colors
    val tasks = state.monthTasksByDate
    val weeks = state.days.chunked(7)
    Column(modifier.calendarMonthGestures(onIntent).testTag("calendar-grid").clipToBounds().border(1.dp, colors.border).padding(1.dp)) {
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").forEach { name ->
                    Text(name, Modifier.weight(1f).padding(horizontal = 12.dp), color = colors.muted, style = TaskBTheme.typography.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Divider(Modifier.align(Alignment.BottomCenter), color = colors.border)
        }
        weeks.forEachIndexed { weekIndex, week ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                week.forEachIndexed { dayIndex, date ->
                    key(date) {
                        CalendarDayCell(date, tasks[date].orEmpty(), state, drag, onIntent, onOpenTask,
                            endBorder = dayIndex < 6, bottomBorder = weekIndex < weeks.lastIndex,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(date: LocalDate, tasks: List<Task>, state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (Task) -> Unit, endBorder: Boolean, bottomBorder: Boolean, modifier: Modifier) {
    val colors = TaskBTheme.colors
    val outsideMonth = date.yearMonth != state.month
    val isToday = date == state.today
    val hovered = drag.hoveredDay == date
    val isSelected = date == state.selectedDate
    DisposableEffect(drag, date) { onDispose { drag.targets.remove(date) } }
    Box(modifier
        .onGloballyPositioned { drag.targets[date] = it.boundsInRoot() }
        .background(when { hovered || isSelected -> colors.selected; outsideMonth || date.dayOfWeek.isoDayNumber >= 6 -> colors.subtle; else -> colors.surface })
        .clickable(interactionSource = null, indication = null, role = Role.Button) { onIntent(CalendarIntent.SelectDay(date)) }
        .semantics { contentDescription = "Show tasks for $date"; selected = hovered || isSelected }
        .testTag("calendar-day-$date").clipToBounds()) {
        Column(Modifier.fillMaxSize().padding(end = if (endBorder) 1.dp else 0.dp, bottom = if (bottomBorder) 1.dp else 0.dp).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(20.dp)
                    .then(if (isToday) Modifier.background(colors.actionFill, RoundedCornerShape(4.dp)) else Modifier)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onIntent(CalendarIntent.SelectDay(date)) }
                    .semantics { contentDescription = "Show tasks for $date" }
                    .testTag("calendar-day-number-$date"), contentAlignment = Alignment.Center) {
                    Text(date.day.toString(), color = if (isToday) colors.onAction else if (outsideMonth) colors.muted else colors.text, style = TaskBTheme.typography.button)
                }
                if (outsideMonth) Text(calendarMonthAbbreviation.format(date), color = colors.muted, style = TaskBTheme.typography.button)
            }
            CalendarDayTasks(date, tasks, state, drag, onIntent, onOpenTask, { onIntent(CalendarIntent.SelectDay(date)) }, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2.dp))
        }
        if (endBorder) Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(colors.border))
        if (bottomBorder) Divider(Modifier.align(Alignment.BottomCenter), color = colors.border)
    }
}

/** Учитываем высоту перенесённых названий и ссылки на остальные задачи, чтобы не обрезать строки. */
@Composable
private fun CalendarDayTasks(date: LocalDate, tasks: List<Task>, state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (Task) -> Unit, onSelectDay: () -> Unit, modifier: Modifier) {
    val colors = TaskBTheme.colors
    val titleStyle = calendarTaskTitleStyle(dense = true)
    val moreStyle = TaskBTheme.typography.body.copy(
        lineHeight = 18.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val textMeasurer = rememberTextMeasurer()
    SubcomposeLayout(modifier) { constraints ->
        val rows = buildList<Placeable> {
            var measuredHeight = 0
            for (task in tasks) {
                val timed = task.startDateTime != null
                val group = state.groups.find { it.id == task.groupId }
                val taskInsets = when {
                    !timed -> calendarTaskPadding.roundToPx() * 2 + calendarTaskCheckboxSize.roundToPx() + calendarTaskTitleGap.roundToPx()
                    group != null -> 2.dp.roundToPx() + 4.dp.roundToPx()
                    else -> 0
                }
                val titleMaxWidth = (constraints.maxWidth - taskInsets).coerceAtLeast(0)
                val label = if (timed) calendarMonthTaskText(task, date) else task.name
                val maxLines = if (timed && task.isMultiDayCalendarEvent) 1 else 2
                val titleLayout = textMeasurer.measure(label, style = titleStyle, maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = titleMaxWidth))
                val titleWidth = if (titleLayout.multiParagraph.didExceedMaxLines) titleMaxWidth else {
                    ceil((0 until titleLayout.lineCount).maxOf { titleLayout.getLineRight(it) - titleLayout.getLineLeft(it) })
                        .toInt().coerceIn(0, titleMaxWidth)
                }
                val taskWidth = (taskInsets + titleWidth).coerceAtMost(constraints.maxWidth)
                val row = subcompose(task.key) {
                    val enabled = task.id !in state.pendingCompletion && state.pendingMove == null
                    CalendarTaskRow(task, group, { onOpenTask(task) }, { onIntent(CalendarIntent.ToggleCompleted(it, task.occurrenceId)) },
                        Modifier.testTag("calendar-task-${task.key}").calendarTaskDrag(task, drag, enabled, dense = true, sourceDate = date) { id, target -> onIntent(CalendarIntent.MoveTask(id, target, task.occurrenceId)) },
                        dense = true, enabled = enabled, dragging = drag.task?.key == task.key, date = date, now = state.now)
                }.single().measure(Constraints.fixedWidth(taskWidth))
                add(row)
                measuredHeight += row.height
                if (measuredHeight > constraints.maxHeight) break
            }
        }
        val moreHeight = textMeasurer.measure(
            text = "+${tasks.size} more", style = moreStyle, maxLines = 1,
            constraints = Constraints(maxWidth = (constraints.maxWidth - calendarTaskTitleInset.roundToPx()).coerceAtLeast(0)),
        ).size.height
        var visible = rows.size
        var rowsHeight = rows.sumOf { it.height }
        while (visible > 0 && rowsHeight + (if (tasks.size > visible) moreHeight else 0) > constraints.maxHeight) {
            visible--
            rowsHeight -= rows[visible].height
        }
        val more = if (tasks.size > visible) subcompose("more") {
            Text("+${tasks.size - visible} more", color = colors.accent, style = moreStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = calendarTaskTitleInset).clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onSelectDay))
        }.single().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = (constraints.maxHeight - rowsHeight).coerceAtLeast(0))) else null
        layout(constraints.maxWidth, constraints.maxHeight) {
            var y = 0
            rows.take(visible).forEach { row -> row.placeRelative(0, y); y += row.height }
            more?.placeRelative(0, y)
        }
    }
}
