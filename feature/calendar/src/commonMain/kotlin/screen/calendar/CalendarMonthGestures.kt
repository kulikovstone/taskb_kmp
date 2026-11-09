package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Composable
internal fun Modifier.calendarMonthGestures(onIntent: (CalendarIntent) -> Unit): Modifier {
    val latestIntent by rememberUpdatedState(newValue = onIntent)
    return pointerInput(key1 = Unit) {
            var lastScroll: TimeMark? = null
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.sumOf { change -> change.scrollDelta.y.toDouble() }.toFloat()
                        if (delta != 0f) {
                            if (lastScroll?.elapsedNow()?.inWholeMilliseconds?.let { elapsedMillis ->
                                elapsedMillis >= 250
                            } != false) {
                                val intent = if (event.keyboardModifiers.isCtrlPressed) {
                                    if (delta > 0) CalendarIntent.NextYear else CalendarIntent.PreviousYear
                                } else {
                                    if (delta > 0) CalendarIntent.NextMonth else CalendarIntent.PreviousMonth
                                }
                                latestIntent(intent)
                                lastScroll = TimeSource.Monotonic.markNow()
                            }
                            event.changes.forEach(action = PointerInputChange::consume)
                        }
                    }
                }
            }
        }
        .pointerInput(key1 = Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type != PointerType.Touch) return@awaitEachGesture
                var distance = 0f
                val start = awaitHorizontalTouchSlopOrCancellation(pointerId = down.id) { change, over ->
                    distance = over
                    change.consume()
                } ?: return@awaitEachGesture
                val ended = horizontalDrag(pointerId = start.id) { change ->
                    distance += change.positionChange().x
                    change.consume()
                }
                if (ended && abs(x = distance) >= 48.dp.toPx()) {
                    latestIntent(if (distance < 0) CalendarIntent.NextMonth else CalendarIntent.PreviousMonth)
                }
            }
        }
}
