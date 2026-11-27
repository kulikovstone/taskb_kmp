package com.mkulikov.taskb.feature.item.screen.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.item.api.model.TaskFilter
import com.mkulikov.taskb.feature.item.component.SelectionDialog
import com.mkulikov.taskb.feature.item.component.SelectionScreen
import com.mkulikov.taskb.ui.component.TaskBSelectionRow
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar

@Composable
internal fun TaskSelectionScreen(state: TaskState, onIntent: (TaskIntent) -> Unit) {
    SelectionScreen(title = "Filters", onClose = { onIntent(TaskIntent.CloseSelection) }) { modifier ->
        TaskSelectionContent(state = state, onIntent = onIntent, modifier = modifier)
    }
}

@Composable
internal fun TaskSelectionDialog(state: TaskState, onIntent: (TaskIntent) -> Unit, availableHeight: Dp) {
    SelectionDialog(
        title = "Filters",
        onClose = { onIntent(TaskIntent.CloseSelection) },
        availableHeight = availableHeight,
        preferredHeight = 496.dp,
    ) { modifier ->
        TaskSelectionContent(state = state, onIntent = onIntent, modifier = modifier, scrollbar = true)
    }
}

@Composable
private fun TaskSelectionContent(
    state: TaskState,
    onIntent: (TaskIntent) -> Unit,
    modifier: Modifier,
    scrollbar: Boolean = false,
) {
    val scroll = rememberLazyListState()
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().padding(end = if (scrollbar) 20.dp else 0.dp),
        ) {
            items(items = TaskFilter.entries, key = TaskFilter::name) { filter ->
                TaskBSelectionRow(
                    label = filter.label,
                    selected = state.filter == filter,
                    onClick = { onIntent(TaskIntent.Filter(filter = filter)) },
                )
            }
        }
        if (scrollbar && (scroll.canScrollForward || scroll.canScrollBackward))
            TaskBVerticalScrollbar(
                state = scroll,
                modifier = Modifier.align(alignment = Alignment.CenterEnd).fillMaxHeight(),
            )
    }
}
