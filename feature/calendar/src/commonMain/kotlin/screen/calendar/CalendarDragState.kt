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
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.LocalDate

@Stable
internal class CalendarDragState {
    var task: Task? by mutableStateOf(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    var size by mutableStateOf(IntSize.Zero)
        private set
    var dense by mutableStateOf(false)
        private set
    val targets = mutableStateMapOf<LocalDate, Rect>()
    val hoveredDay: LocalDate? get() = if (task == null) null else targets.entries.firstOrNull { it.value.contains(position) }?.key

    fun begin(task: Task, position: Offset, size: IntSize, dense: Boolean) {
        this.task = task
        this.position = position
        this.size = size
        this.dense = dense
    }
    fun update(position: Offset) { this.position = position }
    fun cancel() { task = null }
    fun finish(onMove: (Int, LocalDate) -> Unit) {
        val source = task
        val date = hoveredDay
        cancel()
        if (source != null && date != null && date != source.dueDate) onMove(source.id, date)
    }
}

/** Mouse dragging starts after slop; touch dragging starts after a long press, leaving swipes to the month grid. */
@Composable
internal fun Modifier.calendarTaskDrag(
    task: Task,
    dragState: CalendarDragState,
    enabled: Boolean,
    dense: Boolean = false,
    onMove: (Int, LocalDate) -> Unit,
): Modifier {
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    val latestTask by rememberUpdatedState(task)
    val latestMove by rememberUpdatedState(onMove)
    return onGloballyPositioned { coordinates = it }.pointerInput(task.id, enabled, dragState, dense) {
        if (!enabled) return@pointerInput
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = if (down.type == PointerType.Mouse) {
                    awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                } else {
                    awaitLongPressOrCancellation(down.id)
                } ?: return@awaitEachGesture
                start.consume()
                val origin = coordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
                dragState.begin(latestTask, origin.localToRoot(start.position), origin.size, dense)
                val ended = drag(start.id) { change ->
                    change.consume()
                    coordinates?.takeIf { it.isAttached }?.let { dragState.update(it.localToRoot(change.position)) }
                }
                if (ended) dragState.finish(latestMove) else dragState.cancel()
            }
        } finally {
            if (dragState.task?.id == task.id) dragState.cancel()
        }
    }
}
