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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.yearMonth

@Composable
internal fun CalendarMonthGrid(state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (Int) -> Unit, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val colors = TaskBTheme.colors
    val tasks = state.tasksByDate
    val weeks = state.days.chunked(7)
    Column(modifier.calendarMonthGestures(onIntent).testTag("calendar-grid").clip(RoundedCornerShape(8.dp)).border(1.dp, colors.border, RoundedCornerShape(8.dp))) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").forEach { name ->
                Text(name, Modifier.weight(1f).padding(start = 12.dp), color = colors.muted, style = TaskBTheme.typography.small, maxLines = 1)
            }
        }
        Divider(color = colors.border)
        weeks.forEachIndexed { weekIndex, week ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                week.forEachIndexed { dayIndex, date ->
                    key(date) {
                        CalendarDayCell(date, tasks[date].orEmpty(), state, drag, onIntent, onOpenTask, onOpenDay, Modifier.weight(1f).fillMaxHeight())
                    }
                    if (dayIndex < 6) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                }
            }
            if (weekIndex < weeks.lastIndex) Divider(color = colors.border)
        }
    }
}

@Composable
private fun CalendarDayCell(date: LocalDate, tasks: List<Task>, state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (Int) -> Unit, onOpenDay: (LocalDate) -> Unit, modifier: Modifier) {
    val colors = TaskBTheme.colors
    val outsideMonth = date.yearMonth != state.month
    val hovered = drag.hoveredDay == date
    DisposableEffect(drag, date) { onDispose { drag.targets.remove(date) } }
    Column(modifier
        .onGloballyPositioned { drag.targets[date] = it.boundsInRoot() }
        .background(when { hovered -> colors.selected; outsideMonth || date.dayOfWeek.isoDayNumber >= 6 -> colors.subtle; else -> colors.surface })
        .clickable(role = Role.Button) { onOpenDay(date) }
        .semantics { contentDescription = "Open day $date"; selected = hovered }
        .testTag("calendar-day-$date").clipToBounds().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(20.dp).then(if (date == state.today) Modifier.background(colors.actionFill, RoundedCornerShape(8.dp)) else Modifier), contentAlignment = Alignment.Center) {
                    Text(date.day.toString(), color = if (date == state.today) colors.onAction else if (outsideMonth) colors.muted else colors.text, style = TaskBTheme.typography.label)
                }
                if (outsideMonth) Text(calendarMonthAbbreviation.format(date), color = colors.muted, style = TaskBTheme.typography.small)
            }
            CalendarDayTasks(tasks, state, drag, onIntent, onOpenTask, { onOpenDay(date) }, Modifier.weight(1f).fillMaxWidth())
    }
}

/** Measure wrapped task names before reserving the overflow action, so no task is silently clipped. */
@Composable
private fun CalendarDayTasks(tasks: List<Task>, state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (Int) -> Unit, onOpenDay: () -> Unit, modifier: Modifier) {
    val colors = TaskBTheme.colors
    val typography = TaskBTheme.typography
    SubcomposeLayout(modifier) { constraints ->
        val gap = 2.dp.roundToPx()
        val rows = tasks.take(5).map { task ->
            subcompose(task.id) {
                val enabled = task.id !in state.pendingCompletion && state.pendingMove == null
                CalendarTaskRow(task, state.groups.find { it.id == task.groupId }, onOpenTask, { onIntent(CalendarIntent.ToggleCompleted(it)) },
                    Modifier.testTag("calendar-task-${task.id}").calendarTaskDrag(task, drag, enabled) { id, target -> onIntent(CalendarIntent.MoveTask(id, target)) },
                    dense = true, enabled = enabled, dragging = drag.task?.id == task.id)
            }.single().measure(Constraints.fixedWidth(constraints.maxWidth))
        }
        var visible = rows.size
        fun rowsHeight(count: Int) = rows.take(count).sumOf { it.height } + gap * (count - 1).coerceAtLeast(0)
        while (visible > 0 && rowsHeight(visible) + (if (tasks.size > visible) 18.dp.roundToPx() + gap else 0) > constraints.maxHeight) visible--
        val more = if (tasks.size > visible) subcompose("more") {
            Text("+${tasks.size - visible} more", color = colors.accent, style = typography.small, maxLines = 1,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenDay))
        }.single().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = (constraints.maxHeight - rowsHeight(visible) - if (visible > 0) gap else 0).coerceAtLeast(0))) else null
        layout(constraints.maxWidth, constraints.maxHeight) {
            var y = 0
            rows.take(visible).forEach { row -> row.placeRelative(0, y); y += row.height + gap }
            more?.placeRelative(0, y)
        }
    }
}
