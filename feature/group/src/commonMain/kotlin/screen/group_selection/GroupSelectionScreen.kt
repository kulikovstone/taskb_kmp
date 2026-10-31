package com.mkulikov.taskb.feature.group.screen.group_selection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.GroupSelectionRoute
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBSelectionRow
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.theme.TaskBTheme
import org.koin.compose.koinInject

@Composable
internal fun GroupSelectionScreen(route: GroupSelectionRoute, onBack: () -> Unit, onSelected: (GroupSelectionResult) -> Unit) {
    val repository = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource("group-selection") {
        GroupSelectionStore(repository, route.selectedGroupId, route.showNoGroup, route.noGroupSelected)
    }
    val state by store.state.collectAsState()
    LaunchedEffect(state.result) { state.result?.let(onSelected) }
    Surface(Modifier.fillMaxSize()) {
        GroupSelectionContent(state, store::accept, onBack)
    }
}

@Composable
internal fun GroupSelectionContent(state: GroupSelectionState, onIntent: (GroupSelectionIntent) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TaskBTopAppBar("Choose group", onBack, titleSpacing = 4.dp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 768.dp).fillMaxSize().padding(24.dp)) {
                TaskBTextField(state.query, { onIntent(GroupSelectionIntent.Search(it)) }, "Search groups",
                    placeholder = "Search groups", search = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                val modifier = Modifier.weight(1f).fillMaxWidth()
                when {
                    state.isLoading -> TaskBLoading(modifier)
                    state.loadError != null -> TaskBLoadError(state.loadError, { onIntent(GroupSelectionIntent.RetryLoad) }, modifier)
                    else -> GroupSelectionList(state, onIntent, modifier)
                }
            }
        }
    }
}

@Composable
private fun GroupSelectionList(state: GroupSelectionState, onIntent: (GroupSelectionIntent) -> Unit, modifier: Modifier) {
    val scroll = rememberLazyListState()
    val scrollbar = scroll.canScrollForward || scroll.canScrollBackward
    Box(modifier.selectableGroup()) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = if (scrollbar) 20.dp else 0.dp)) {
            if (state.isNoGroupVisible) item(key = "none") {
                TaskBSelectionRow("No group", state.noGroupSelected,
                    { onIntent(GroupSelectionIntent.Select(null)) }, reserveGroupIconSpace = true)
            }
            items(state.matchingGroups, key = { it.id }) { group ->
                TaskBSelectionRow(group.name, state.selectedGroupId == group.id,
                    { onIntent(GroupSelectionIntent.Select(group.id)) }, group.color.color)
            }
            if (state.matchingGroups.isEmpty() && !state.isNoGroupVisible) item {
                Text(if (state.query.isBlank()) "No groups yet." else "No groups match your search.",
                    color = TaskBTheme.colors.muted, modifier = Modifier.padding(vertical = 24.dp))
            }
        }
        if (scrollbar) TaskBVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
