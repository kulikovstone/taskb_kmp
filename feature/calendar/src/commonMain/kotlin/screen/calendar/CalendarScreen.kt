package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.theme.TaskBTheme
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.ui.dialog.TaskBChoiceDialog
import com.mkulikov.taskb.ui.dialog.TaskBDialogChoice
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

@Composable
internal fun CalendarScreen(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onCreateTask: (LocalDate?) -> Unit,
    onOpenOccurrence: ((Task) -> Unit)? = null,
) {
    val drag = remember { CalendarDragState() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val openTask: (Task) -> Unit = { onOpenOccurrence?.invoke(it) ?: onOpenTask(it.id) }
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
                            CalendarSidebar(state, drag, onIntent, openTask)
                            CalendarMonthGrid(state, drag, onIntent, openTask, Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp, bottom = 24.dp))
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
                    val density = LocalDensity.current
                    val dense = drag.dense
                    val horizontalPadding = if (dense) calendarTaskTitleGap - calendarTaskPadding else 8.dp
                    val verticalPadding = 2.dp
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset((drag.position.x - origin.x + 12.dp.toPx()).roundToInt(), (drag.position.y - origin.y + 12.dp.toPx()).roundToInt()) }
                            .requiredSize(
                                with(density) { drag.size.width.toDp() } + horizontalPadding * 2,
                                with(density) { drag.size.height.toDp() } + verticalPadding * 2,
                            )
                            .testTag("calendar-drag-preview"),
                        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, TaskBTheme.colors.accent),
                        color = TaskBTheme.colors.surface, elevation = 4.dp,
                    ) {
                        CalendarTaskRow(
                            task, state.groups.find { it.id == task.groupId }, {}, {},
                            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                            dense = dense, enabled = false,
                        )
                    }
                }
            }
        }
    }
    state.moveChoice?.let { move ->
        TaskBChoiceDialog("Move repeating task", "Move only this event, or start the remaining schedule on ${move.date}? Completed events keep their dates.", listOf(
            TaskBDialogChoice("Only this event", { onIntent(CalendarIntent.ConfirmMove(TaskChangeScope.ThisOccurrence)) }),
            TaskBDialogChoice("This and following events", { onIntent(CalendarIntent.ConfirmMove(TaskChangeScope.ThisAndFollowing)) }),
        ), { onIntent(CalendarIntent.DismissMoveChoice) })
    }
}

@Preview(name = "Calendar", widthDp = 1440, heightDp = 900)
@Composable
private fun CalendarScreenPreview() {
    TaskBTheme(darkTheme = false) { CalendarScreen(calendarPreviewState(), {}, {}, {}, {}) }
}
