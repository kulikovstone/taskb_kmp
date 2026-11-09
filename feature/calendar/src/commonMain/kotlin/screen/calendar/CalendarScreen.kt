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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.dialog.TaskBChoiceDialog
import com.mkulikov.taskb.ui.dialog.TaskBDialogChoice
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

@Composable
internal fun CalendarScreen(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onCreateTask: (LocalDate?) -> Unit,
    onOpenOccurrence: ((Task) -> Unit)? = null,
    onCreateEvent: (LocalDate?) -> Unit = {},
    onOpenEvent: (Event) -> Unit = {},
) {
    val drag = remember { CalendarDragState() }
    var origin by remember { mutableStateOf(value = Offset.Zero) }
    val openTask: (Task) -> Unit = { task -> onOpenOccurrence?.invoke(task) ?: onOpenTask(task.id) }
    val openItem: (CalendarItem) -> Unit = { item ->
        when (item) {
            is CalendarItem.TaskItem -> openTask(item.task)
            is CalendarItem.EventItem -> onOpenEvent(item.event)
            is CalendarItem.HolidayItem -> Unit
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    origin = coordinates.positionInRoot()
                }
        ) {
            if (maxWidth < 840.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TaskBTopAppBar(title = "Calendar", onBack = onBack)
                    Box(
                        modifier = Modifier.weight(weight = 1f).fillMaxWidth().padding(all = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Calendar is available in wide windows.", color = TaskBTheme.colors.muted)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    CalendarTopBar(
                        state = state,
                        onIntent = onIntent,
                        onBack = onBack,
                        onCreateTask = { onCreateTask(state.selectedDate) },
                        onCreateEvent = { onCreateEvent(state.selectedDate) },
                    )
                    when {
                        state.isLoading -> TaskBLoading(modifier = Modifier.weight(weight = 1f))
                        state.loadError != null -> TaskBLoadError(
                            message = state.loadError,
                            onRetry = { onIntent(CalendarIntent.RetryLoad) },
                        )
                        else -> Row(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                            CalendarSidebar(
                                state = state,
                                drag = drag,
                                onIntent = onIntent,
                                onOpenTask = openItem,
                            )
                            CalendarMonthGrid(
                                state = state,
                                drag = drag,
                                onIntent = onIntent,
                                onOpenTask = openItem,
                                modifier = Modifier.weight(weight = 1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                if (state.error != null) {
                    Surface(
                        modifier = Modifier
                            .align(alignment = Alignment.BottomEnd)
                            .padding(all = 34.dp)
                            .widthIn(max = 480.dp),
                        shape = RoundedCornerShape(size = 8.dp),
                        color = TaskBTheme.colors.dangerSoft,
                        elevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.error,
                                color = TaskBTheme.colors.danger,
                                style = TaskBTheme.typography.body,
                                modifier = Modifier.weight(weight = 1f, fill = false),
                            )
                            TextButton(onClick = { onIntent(CalendarIntent.DismissError) }) {
                                Text(text = "Dismiss", color = TaskBTheme.colors.danger)
                            }
                        }
                    }
                } else {
                    state.undo?.let { move ->
                        Surface(
                            modifier = Modifier
                                .align(alignment = Alignment.BottomEnd)
                                .padding(all = 34.dp)
                                .testTag(tag = "calendar-undo"),
                            shape = RoundedCornerShape(size = 8.dp),
                            color = TaskBTheme.colors.inverseSurface,
                            elevation = 4.dp,
                        ) {
                            Row(
                                modifier = Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(space = 24.dp),
                            ) {
                                Text(
                                    text =
                                        ("${if (move.isEvent) "Event" else "Task"} moved to " +
                                            "${calendarDateLabel.format(value = move.date)}"),
                                    color = TaskBTheme.colors.onInverseSurface,
                                    style = TaskBTheme.typography.body,
                                )
                                TextButton(
                                    onClick = { onIntent(CalendarIntent.UndoMove) },
                                    enabled = state.pendingMove == null && move.taskId !in state.pendingCompletion,
                                ) {
                                    Text(
                                        text = "Undo",
                                        color = TaskBTheme.colors.inverseAccent,
                                        style = TaskBTheme.typography.button,
                                    )
                                }
                            }
                        }
                    }
                }
                drag.item?.let { task ->
                    val density = LocalDensity.current
                    val dense = drag.dense
                    val horizontalPadding = when {
                        dense -> calendarTaskTitleGap - calendarTaskPadding
                        task.startDateTime != null -> 8.dp
                        else -> calendarSidebarTaskTitleGap(task = task)
                    }
                    val verticalPadding = 2.dp
                    val sourceHorizontalPadding = if (dense) {
                        0.dp
                    } else {
                        calendarSidebarTaskPadding.calculateLeftPadding(layoutDirection = LayoutDirection.Ltr) +
                            calendarSidebarTaskPadding.calculateRightPadding(
                                layoutDirection = LayoutDirection.Ltr
                            )
                    }
                    Surface(
                        modifier = Modifier
                            .offset {
                                    IntOffset(
                                        x = (drag.position.x - origin.x + 12.dp.toPx()).roundToInt(),
                                        y = (drag.position.y - origin.y + 12.dp.toPx()).roundToInt(),
                                    )
                                }
                            .requiredSize(
                                width = with(receiver = density) { drag.size.width.toDp() } -
                                    sourceHorizontalPadding + horizontalPadding * 2,
                                height = with(receiver = density) { drag.size.height.toDp() } + verticalPadding * 2,
                            )
                            .testTag(tag = "calendar-drag-preview"),
                        shape = RoundedCornerShape(size = 8.dp),
                        border = BorderStroke(width = 1.dp, color = TaskBTheme.colors.accent),
                        color = TaskBTheme.colors.surface,
                        elevation = 4.dp,
                    ) {
                        CalendarTaskRow(
                            task = task,
                            group = state.groups.find { group -> group.id == task.groupId },
                            onOpenTask = {},
                            onToggle = {},
                            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                            dense = dense,
                            enabled = false,
                            date = drag.sourceDate,
                            now = state.now,
                            today = state.today,
                        )
                    }
                }
            }
        }
    }
    state.moveChoice?.let { move ->
        TaskBChoiceDialog(
            title = if (move.isEvent) "Move repeating event" else "Move repeating task",
            message = "Move only this event, or start the remaining schedule on ${move.date}?" +
                if (move.isEvent) "" else " Completed events keep their dates.",
            choices = listOf(
                TaskBDialogChoice(
                    label = "Only this event",
                    onClick = { onIntent(CalendarIntent.ConfirmMove(scope = TaskChangeScope.ThisOccurrence)) },
                ),
                TaskBDialogChoice(
                    label = "This and following events",
                    onClick = {
                        onIntent(CalendarIntent.ConfirmMove(scope = TaskChangeScope.ThisAndFollowing))
                    },
                ),
            ),
            onDismiss = { onIntent(CalendarIntent.DismissMoveChoice) },
        )
    }
}

@Preview(name = "Calendar", widthDp = 1440, heightDp = 900)
@Composable
private fun CalendarScreenPreview() {
    TaskBTheme(darkTheme = false) {
        CalendarScreen(
            state = calendarPreviewState(),
            onIntent = {},
            onBack = {},
            onOpenTask = {},
            onCreateTask = {},
        )
    }
}

@Preview(name = "Calendar with timed tasks", widthDp = 1440, heightDp = 900)
@Composable
private fun CalendarTimedScreenPreview() {
    TaskBTheme(darkTheme = false) {
        CalendarScreen(
            state = timedCalendarPreviewState(),
            onIntent = {},
            onBack = {},
            onOpenTask = {},
            onCreateTask = {},
        )
    }
}
