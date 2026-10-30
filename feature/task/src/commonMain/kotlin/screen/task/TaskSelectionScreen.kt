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
import com.mkulikov.taskb.feature.task.component.GroupSelectionContent
import com.mkulikov.taskb.feature.task.component.SelectionDialog
import com.mkulikov.taskb.feature.task.component.SelectionRow
import com.mkulikov.taskb.feature.task.component.SelectionScreen
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar

@Composable
internal fun TaskSelectionScreen(state: TaskState, onIntent: (TaskIntent) -> Unit) {
    SelectionScreen(if (state.selection == TaskSelection.Filters) "Filters" else "Choose group", { onIntent(TaskIntent.CloseSelection) }) {
        TaskSelectionContent(state, onIntent, it)
    }
}

@Composable
internal fun TaskSelectionDialog(state: TaskState, onIntent: (TaskIntent) -> Unit, availableHeight: Dp) {
    val filters = state.selection == TaskSelection.Filters
    SelectionDialog(if (filters) "Filters" else "Choose group", { onIntent(TaskIntent.CloseSelection) }, availableHeight,
        if (filters) 496.dp else 624.dp) { TaskSelectionContent(state, onIntent, it, scrollbar = true) }
}

@Composable
private fun TaskSelectionContent(state: TaskState, onIntent: (TaskIntent) -> Unit, modifier: Modifier, scrollbar: Boolean = false) {
    if (state.selection == TaskSelection.Filters) {
        val scroll = rememberLazyListState()
        Box(modifier.fillMaxWidth()) {
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = if (scrollbar) 20.dp else 0.dp)) {
                items(TaskFilter.entries, key = { it.name }) { filter -> SelectionRow(filter.label, state.filter == filter, { onIntent(TaskIntent.Filter(filter)) }) }
            }
            if (scrollbar && (scroll.canScrollForward || scroll.canScrollBackward)) TaskBVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    } else GroupSelectionContent(state.groups, (state.groupFilter as? TaskGroupFilter.Selected)?.id, state.groupFilter == TaskGroupFilter.NoGroup,
        state.groupQuery, { onIntent(TaskIntent.SearchGroups(it)) },
        { onIntent(TaskIntent.FilterGroup(it?.let(TaskGroupFilter::Selected) ?: TaskGroupFilter.NoGroup)) }, modifier, scrollbar)
}
