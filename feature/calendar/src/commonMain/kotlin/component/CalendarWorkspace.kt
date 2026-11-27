package com.mkulikov.taskb.feature.calendar.component

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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarDragState
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarIntent
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarState
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarTaskRow
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarDateLabel
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarSidebarTaskTitleGap
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarTaskPadding
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarTaskTitleGap
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.ui.dialog.TaskBChoiceDialog
import com.mkulikov.taskb.ui.dialog.TaskBDialogChoice
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.math.roundToInt

@Composable
internal fun CalendarWorkspace(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    content: @Composable (CalendarDragState) -> Unit,
) {
    val drag = remember { CalendarDragState() }
    var origin by remember { mutableStateOf(value = Offset.Zero) }
    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates -> origin = coordinates.positionInRoot() },
        ) {
            content(drag)
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
                                    ("${move.type.name} moved to " +
                                        "${calendarDateLabel.format(value = move.date)}"),
                                color = TaskBTheme.colors.onInverseSurface,
                                style = TaskBTheme.typography.body,
                            )
                            TextButton(
                                onClick = { onIntent(CalendarIntent.UndoMove) },
                                enabled = state.canUndo(move = move),
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
    state.moveChoice?.let { move ->
        TaskBChoiceDialog(
            title = "Move repeating ${move.type.name.lowercase()}",
            message = "Move only this event, or start the remaining schedule on ${move.date}?" +
                if (move.type == ItemType.Event) "" else " Completed events keep their dates.",
            choices = listOf(
                TaskBDialogChoice(
                    label = "Only this event",
                    onClick = { onIntent(CalendarIntent.ConfirmMove(scope = ItemChangeScope.ThisOccurrence)) },
                ),
                TaskBDialogChoice(
                    label = "This and following events",
                    onClick = {
                        onIntent(CalendarIntent.ConfirmMove(scope = ItemChangeScope.ThisAndFollowing))
                    },
                ),
            ),
            onDismiss = { onIntent(CalendarIntent.DismissMoveChoice) },
        )
    }
}
