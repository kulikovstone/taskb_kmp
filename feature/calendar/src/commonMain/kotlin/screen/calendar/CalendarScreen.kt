package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

@Composable
internal fun CalendarScreen(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onCreateTask: (LocalDate?) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val drag = remember { CalendarDragState() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
            if (maxWidth < 840.dp) {
                Column(Modifier.fillMaxSize()) {
                    TaskBTopAppBar("Calendar", onBack)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Calendar is available in wide windows.", color = TaskBTheme.colors.muted)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    CalendarTopBar(state, onIntent, onBack, { onCreateTask(null) })
                    when {
                        state.isLoading -> TaskBLoading(Modifier.weight(1f))
                        state.loadError != null -> TaskBLoadError(state.loadError, { onIntent(CalendarIntent.RetryLoad) })
                        else -> Row(Modifier.weight(1f).fillMaxWidth()) {
                            CalendarUndatedTasks(state, drag, onIntent, onOpenTask)
                            CalendarMonthGrid(state, drag, onIntent, onOpenTask, { onCreateTask(it) }, onOpenDay, Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp, bottom = 24.dp))
                        }
                    }
                }
                if (state.error != null) Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(34.dp).widthIn(max = 480.dp),
                    shape = RoundedCornerShape(8.dp), color = TaskBTheme.colors.dangerSoft, elevation = 4.dp,
                ) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.error, color = TaskBTheme.colors.danger, style = TaskBTheme.typography.body, modifier = Modifier.weight(1f, fill = false))
                        TextButton({ onIntent(CalendarIntent.DismissError) }) { Text("Dismiss", color = TaskBTheme.colors.danger) }
                    }
                } else state.undo?.let { move ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(34.dp).testTag("calendar-undo"),
                        shape = RoundedCornerShape(8.dp), color = TaskBTheme.colors.inverseSurface, elevation = 4.dp,
                    ) {
                        Row(Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text("Task moved to ${calendarDateLabel.format(move.date)}", color = TaskBTheme.colors.onInverseSurface, style = TaskBTheme.typography.body)
                            TextButton({ onIntent(CalendarIntent.UndoMove) }, enabled = state.pendingMove == null && move.taskId !in state.pendingCompletion) {
                                Text("Undo", color = TaskBTheme.colors.inverseAccent, style = TaskBTheme.typography.button)
                            }
                        }
                    }
                }
                drag.task?.let { task ->
                    Surface(
                        modifier = Modifier.offset { IntOffset((drag.position.x - origin.x + 12.dp.toPx()).roundToInt(), (drag.position.y - origin.y + 12.dp.toPx()).roundToInt()) }.width(220.dp),
                        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, TaskBTheme.colors.accent),
                        color = TaskBTheme.colors.surface, elevation = 4.dp,
                    ) {
                        CalendarTaskRow(task, state.groups.find { it.id == task.groupId }, {}, {}, Modifier.padding(2.dp).heightIn(min = 32.dp), dense = true, enabled = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarUndatedTasks(state: CalendarState, drag: CalendarDragState, onIntent: (CalendarIntent) -> Unit, onOpenTask: (Int) -> Unit) {
    val tasks = state.undatedTasks
    val lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    Column(Modifier.width(320.dp).fillMaxHeight().testTag("calendar-sidebar").padding(24.dp)) {
        Text("Unscheduled", style = TaskBTheme.typography.section.copy(lineHeightStyle = lineHeightStyle), color = TaskBTheme.colors.heading)
        Spacer(Modifier.height(4.dp))
        Text("${tasks.size} ${if (tasks.size == 1) "task" else "tasks"}", style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle), color = TaskBTheme.colors.muted)
        Spacer(Modifier.height(16.dp))
        if (tasks.isEmpty()) Text("No unscheduled tasks.", color = TaskBTheme.colors.muted, style = TaskBTheme.typography.body)
        val scroll = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("calendar-undated")) {
                items(tasks, key = { it.id }) { task ->
                    val enabled = task.id !in state.pendingCompletion && state.pendingMove == null
                    CalendarTaskRow(task, state.groups.find { it.id == task.groupId }, onOpenTask, { onIntent(CalendarIntent.ToggleCompleted(it)) },
                        Modifier.testTag("calendar-task-${task.id}").calendarTaskDrag(task, drag, enabled) { id, date -> onIntent(CalendarIntent.MoveTask(id, date)) },
                        enabled = enabled, dragging = drag.task?.id == task.id)
                }
            }
            TaskBVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Preview(name = "Calendar", widthDp = 1440, heightDp = 900)
@Composable
private fun CalendarScreenPreview() {
    TaskBTheme(darkTheme = false) { CalendarScreen(calendarPreviewState(), {}, {}, {}, {}, {}) }
}
