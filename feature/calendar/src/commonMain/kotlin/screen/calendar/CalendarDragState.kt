package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Stable
internal class CalendarDragState {
    var item: CalendarItem? by mutableStateOf(value = null)
        private set

    var position by mutableStateOf(value = Offset.Zero)
        private set

    var size by mutableStateOf(value = IntSize.Zero)
        private set

    var dense by mutableStateOf(value = false)
        private set

    var sourceDate: LocalDate? by mutableStateOf(value = null)
        private set

    val targets = mutableStateMapOf<LocalDate, Rect>()
    val hoveredDay: LocalDate?
        get() = if (item == null) {
            null
        } else {
            targets.entries.firstOrNull { entry ->
                entry.value.contains(offset = position)
            }?.key
        }

    fun begin(
        task: CalendarItem,
        position: Offset,
        size: IntSize,
        dense: Boolean,
        sourceDate: LocalDate? = task.dueDate,
    ) {
        if (task.isHoliday) return
        this.item = task
        this.position = position
        this.size = size
        this.dense = dense
        this.sourceDate = sourceDate
    }

    fun update(position: Offset) {
        this.position = position
    }

    fun cancel() {
        item = null
    }

    fun finish(onMove: (Int, LocalDate) -> Unit) {
        val source = item
        val date = hoveredDay
        val displayedDate = sourceDate
        cancel()
        if (source != null && date != null) {
            // A continuing event is dragged by the day under the pointer, not by its start date.
            val startDate = source.dueDate
            val newStart = if (startDate != null && displayedDate != null) {
                startDate.plus(
                    value = date.toEpochDays() - displayedDate.toEpochDays(),
                    unit = DateTimeUnit.DAY,
                )
            } else {
                date
            }
            if (newStart != source.dueDate) onMove(source.id, newStart)
        }
    }
}

/** Mouse dragging starts after slop; touch dragging starts after a long press, leaving swipes to the month grid. */
@Composable
internal fun Modifier.calendarTaskDrag(
    task: CalendarItem,
    dragState: CalendarDragState?,
    enabled: Boolean,
    dense: Boolean = false,
    sourceDate: LocalDate? = task.dueDate,
    onMove: (Int, LocalDate) -> Unit,
): Modifier {
    if (dragState == null || task.isHoliday) return this
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(value = null) }
    val latestTask by rememberUpdatedState(newValue = task)
    val latestMove by rememberUpdatedState(newValue = onMove)
    val latestSourceDate by rememberUpdatedState(newValue = sourceDate)
    return onGloballyPositioned { currentCoordinates -> coordinates = currentCoordinates }
        .pointerInput(task.key, enabled, dragState, dense, sourceDate) {
            if (!enabled) return@pointerInput
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = if (down.type == PointerType.Mouse) {
                        awaitTouchSlopOrCancellation(pointerId = down.id) { change, _ -> change.consume() }
                    } else {
                        awaitLongPressOrCancellation(pointerId = down.id)
                    } ?: return@awaitEachGesture
                    start.consume()
                    val origin = coordinates?.takeIf { coordinates ->
                        coordinates.isAttached
                    } ?: return@awaitEachGesture
                    dragState.begin(
                        task = latestTask,
                        position = origin.localToRoot(relativeToLocal = start.position),
                        size = origin.size,
                        dense = dense,
                        sourceDate = latestSourceDate,
                    )
                    val ended = drag(pointerId = start.id) { change ->
                        change.consume()
                        coordinates
                            ?.takeIf(predicate = LayoutCoordinates::isAttached)
                            ?.let { coordinates ->
                                dragState.update(position = coordinates.localToRoot(relativeToLocal = change.position))
                            }
                    }
                    if (ended) dragState.finish(onMove = latestMove) else dragState.cancel()
                }
            } finally {
                if (dragState.item?.key == task.key) dragState.cancel()
            }
        }
}
