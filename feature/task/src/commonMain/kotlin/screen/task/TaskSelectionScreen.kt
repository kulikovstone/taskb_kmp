package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.task.component.SelectionDialog
import com.mkulikov.taskb.ui.component.TaskBSelectionRow
import com.mkulikov.taskb.feature.task.component.SelectionScreen
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar

@Composable
internal fun TaskSelectionScreen(state: TaskState, onIntent: (TaskIntent) -> Unit) {
    SelectionScreen("Filters", { onIntent(TaskIntent.CloseSelection) }) {
        TaskSelectionContent(state, onIntent, it)
    }
}

@Composable
internal fun TaskSelectionDialog(state: TaskState, onIntent: (TaskIntent) -> Unit, availableHeight: Dp) {
    SelectionDialog("Filters", { onIntent(TaskIntent.CloseSelection) }, availableHeight,
        496.dp) { TaskSelectionContent(state, onIntent, it, scrollbar = true) }
}

@Composable
private fun TaskSelectionContent(state: TaskState, onIntent: (TaskIntent) -> Unit, modifier: Modifier, scrollbar: Boolean = false) {
    val scroll = rememberLazyListState()
    Box(modifier.fillMaxWidth()) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = if (scrollbar) 20.dp else 0.dp)) {
            items(TaskFilter.entries, key = { it.name }) { filter -> TaskBSelectionRow(filter.label, state.filter == filter, { onIntent(TaskIntent.Filter(filter)) }) }
        }
        if (scrollbar && (scroll.canScrollForward || scroll.canScrollBackward)) TaskBVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
