package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.platform.LocalInspectionMode
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private enum class CalendarSlot { Loading, Content }

private val compositionBudget = 5.milliseconds

@Composable
internal fun CalendarLoadingLayout(
    isLoading: Boolean,
    hasMonthGrid: Boolean,
    content: @Composable (onMonthPrepared: (() -> Unit)?) -> Unit,
) {
    if (LocalInspectionMode.current && !isLoading) {
        content(null)
        return
    }

    val layoutState = remember { SubcomposeLayoutState() }
    val latestContent = rememberUpdatedState(newValue = content)
    var prepared by remember(key1 = isLoading) { mutableStateOf(value = false) }
    var monthPrepared by remember(key1 = isLoading) { mutableStateOf(value = false) }
    val onMonthPrepared = remember(key1 = isLoading) { { monthPrepared = true } }
    val calendar: @Composable () -> Unit = { latestContent.value(if (monthPrepared) null else onMonthPrepared) }

    LaunchedEffect(key1 = isLoading) {
        if (isLoading) return@LaunchedEffect
        val pending = layoutState.createPausedPrecomposition(slotId = CalendarSlot.Content, content = calendar)
        var slot: SubcomposeLayoutState.PrecomposedSlotHandle? = null
        try {
            do {
                val start = TimeSource.Monotonic.markNow()
                if (pending.isComplete || pending.resume { start.elapsedNow() >= compositionBudget }) {
                    slot = pending.apply()
                }
                // Leave a frame for the spinner before measuring a costly first composition.
                if (slot == null || start.elapsedNow() >= compositionBudget) {
                    withFrameNanos { }
                }
            } while (slot == null)
            prepared = true
        } finally {
            if (!prepared) slot?.dispose() ?: pending.cancel()
        }
    }

    SubcomposeLayout(state = layoutState, modifier = Modifier.fillMaxSize()) { constraints ->
        // Measure the real calendar during preparation. Only the placed slot is visible and interactive.
        val calendarPlaceables = if (prepared) {
            subcompose(slotId = CalendarSlot.Content, content = calendar).map { child ->
                child.measure(constraints = constraints)
            }
        } else {
            emptyList()
        }
        val visible = if (prepared && (!hasMonthGrid || monthPrepared)) {
            calendarPlaceables
        } else {
            subcompose(slotId = CalendarSlot.Loading) {
                TaskBLoading(modifier = Modifier.background(color = TaskBTheme.colors.surface), fastRotation = true)
            }.map { child -> child.measure(constraints = constraints) }
        }
        layout(width = constraints.maxWidth, height = constraints.maxHeight) {
            visible.forEach { child -> child.placeRelative(x = 0, y = 0) }
        }
    }
}
