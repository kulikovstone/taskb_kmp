package com.mkulikov.taskb.feature.group.screen.group_selection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.repository.GroupRepository
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.component.TaskBEditorTopAppBar
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBSelectionRow
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import org.koin.compose.koinInject

@Composable
internal fun GroupSelectionScreen(
    route: GroupSelectionRoute,
    onBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onSelected: (GroupSelectionResult) -> Unit,
) {
    val repository = koinInject<GroupRepository>()
    val store = rememberNavEntryResource(key = "group-selection") {
        GroupSelectionStore(
            groupsByUsage = repository.observeGroupsByUsage(),
            selectedGroupId = route.selectedGroupId,
            showNoGroup = route.showNoGroup,
            noGroupSelected = route.noGroupSelected,
        )
    }
    val state by store.state.collectAsState()
    LaunchedEffect(key1 = state.result) { state.result?.let(block = onSelected) }
    Surface(modifier = Modifier.fillMaxSize()) {
        GroupSelectionContent(
            state = state,
            onIntent = store::accept,
            onBack = onBack,
            onCreateGroup = onCreateGroup,
        )
    }
}

@Composable
internal fun GroupSelectionContent(
    state: GroupSelectionState,
    onIntent: (GroupSelectionIntent) -> Unit,
    onBack: () -> Unit,
    onCreateGroup: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(modifier = Modifier.fillMaxSize()) {
            if (wide) {
                TaskBEditorTopAppBar(title = "Choose group", onBack = onBack, backEnabled = true) {
                    TaskBEditorButton(label = "New group", icon = TaskBIcons.Add, onClick = onCreateGroup)
                }
            } else {
                TaskBTopAppBar(title = "Choose group", onBack = onBack, titleSpacing = 4.dp) {
                    IconButton(onClick = onCreateGroup) {
                        Icon(
                            imageVector = TaskBIcons.Add,
                            contentDescription = "Add group",
                            tint = TaskBTheme.colors.accent,
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(weight = 1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.widthIn(max = 768.dp).fillMaxSize().padding(all = 24.dp)) {
                    TaskBTextField(
                        state = state.query,
                        description = "Search groups",
                        placeholder = "Search groups",
                        search = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(height = 16.dp))
                    val modifier = Modifier.weight(weight = 1f).fillMaxWidth()
                    when {
                        state.isLoading -> TaskBLoading(modifier = modifier)
                        state.loadError != null -> TaskBLoadError(
                            message = state.loadError,
                            onRetry = { onIntent(GroupSelectionIntent.RetryLoad) },
                            modifier = modifier,
                        )
                        else -> GroupSelectionList(state = state, onIntent = onIntent, modifier = modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupSelectionList(
    state: GroupSelectionState,
    onIntent: (GroupSelectionIntent) -> Unit,
    modifier: Modifier,
) {
    val scroll = rememberLazyListState()
    val scrollbar = scroll.canScrollForward || scroll.canScrollBackward
    val groups = state.matchingGroups
    Box(modifier = modifier.selectableGroup()) {
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().padding(end = if (scrollbar) 20.dp else 0.dp),
        ) {
            if (state.isNoGroupVisible)
                item(key = "none") {
                    TaskBSelectionRow(
                        label = "No group",
                        selected = state.noGroupSelected,
                        onClick = { onIntent(GroupSelectionIntent.Select(groupId = null)) },
                        reserveGroupIconSpace = true,
                    )
                }
            items(items = groups, key = Group::id) { group ->
                val taskCount = group.tasks.planned
                TaskBSelectionRow(
                    label = group.name,
                    selected = state.selectedGroupId == group.id,
                    onClick = { onIntent(GroupSelectionIntent.Select(groupId = group.id)) },
                    groupColor = group.color.color,
                    trailingText = if (taskCount > 0) taskCount.toString() else null,
                    checkAfterLabel = true,
                )
            }
            if (groups.isEmpty() && !state.isNoGroupVisible)
                item {
                    Text(
                        text = if (state.query.text.isBlank()) "No groups yet." else "No groups match your search.",
                        color = TaskBTheme.colors.muted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
        }
        if (scrollbar)
            TaskBVerticalScrollbar(
                state = scroll,
                modifier = Modifier.align(alignment = Alignment.CenterEnd).fillMaxHeight(),
            )
    }
}
