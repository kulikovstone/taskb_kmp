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
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.time.TimeSource
import kotlin.time.TimeMark

@Composable
internal fun Modifier.calendarMonthGestures(onIntent: (CalendarIntent) -> Unit): Modifier {
    val latestIntent by rememberUpdatedState(onIntent)
    return pointerInput(Unit) {
        var lastScroll: TimeMark? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                    if (delta != 0f) {
                        if (lastScroll?.elapsedNow()?.inWholeMilliseconds?.let { it >= 250 } != false) {
                            latestIntent(if (delta > 0) CalendarIntent.NextMonth else CalendarIntent.PreviousMonth)
                            lastScroll = TimeSource.Monotonic.markNow()
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Touch) return@awaitEachGesture
            var distance = 0f
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                distance = over
                change.consume()
            } ?: return@awaitEachGesture
            val ended = horizontalDrag(start.id) { change -> distance += change.positionChange().x; change.consume() }
            if (ended && abs(distance) >= 48.dp.toPx()) {
                latestIntent(if (distance < 0) CalendarIntent.NextMonth else CalendarIntent.PreviousMonth)
            }
        }
    }
}
