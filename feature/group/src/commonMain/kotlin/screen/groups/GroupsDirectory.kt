package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBSortHeader
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

private const val GroupNameWeight = 0.6f
private const val GroupCountWeight = 0.2f
private val GroupIconSize = 20.dp
private val GroupRowStartPadding = 20.dp
private val GroupNamePadding = 12.dp
private val GroupCountPadding = 8.dp

@Composable
internal fun GroupsFilters(state: GroupsState, onIntent: (GroupsIntent) -> Unit) {
    val colors = TaskBTheme.colors
    Row(
        modifier = Modifier.horizontalScroll(state = rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        listOf(
                GroupFilter.All to "All",
                GroupFilter.WithTasks to "With tasks  ${state.withTasksCount}",
                GroupFilter.Empty to "Empty ${state.emptyCount}",
            )
            .forEach { (filter, label) ->
                val selected = state.filter == filter
                Column(
                    modifier = Modifier
                        .width(intrinsicSize = IntrinsicSize.Max)
                        .height(height = 40.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onIntent(GroupsIntent.Filter(filter = filter)) },
                        )
                ) {
                    Box(
                        modifier = Modifier.weight(weight = 1f).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (selected) colors.accent else colors.secondary,
                            style = TaskBTheme.typography.label,
                            maxLines = 1,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height = 2.dp)
                            .background(color = if (selected) colors.accent else colors.surface)
                    )
                }
            }
    }
}

@Composable
internal fun GroupsDirectory(
    state: GroupsState,
    onIntent: (GroupsIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenGroup: (Int) -> Unit,
) {
    val colors = TaskBTheme.colors
    val groups = state.visibleGroups
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupColumnHeading(
                label = "Name",
                sort = GroupSort.Name,
                state = state,
                onIntent = onIntent,
                modifier = Modifier
                    .weight(weight = GroupNameWeight)
                    .padding(start = GroupRowStartPadding + GroupIconSize + GroupNamePadding),
            )
            GroupColumnHeading(
                label = "Tasks",
                sort = GroupSort.Tasks,
                state = state,
                onIntent = onIntent,
                modifier = Modifier
                    .weight(weight = GroupCountWeight)
                    .padding(start = GroupCountPadding),
            )
            GroupColumnHeading(
                label = "Events",
                sort = GroupSort.Events,
                state = state,
                onIntent = onIntent,
                modifier = Modifier
                    .weight(weight = GroupCountWeight)
                    .padding(start = GroupCountPadding),
            )
        }
        Divider(color = colors.border)
        when {
            state.isLoading -> TaskBLoading(modifier = Modifier.weight(weight = 1f))
            state.loadError != null -> TaskBLoadError(
                message = state.loadError,
                onRetry = { onIntent(GroupsIntent.RetryLoad) },
            )
            groups.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(all = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.groups.isEmpty()) {
                        "No groups yet."
                    } else {
                        "No groups match your search or filter."
                    },
                    style = TaskBTheme.typography.body,
                    color = colors.muted,
                )
            }
            else -> {
                val scroll = rememberLazyListState()
                Box(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize()) {
                        items(items = groups, key = Group::id) { group ->
                            GroupRow(group = group, onClick = { onOpenGroup(group.id) })
                        }
                    }
                    if (scroll.canScrollForward || scroll.canScrollBackward) {
                        TaskBVerticalScrollbar(
                            state = scroll,
                            modifier = Modifier
                                .align(alignment = Alignment.CenterEnd)
                                .fillMaxHeight()
                                .testTag(tag = "group-list-scrollbar"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupColumnHeading(
    label: String,
    sort: GroupSort,
    state: GroupsState,
    onIntent: (GroupsIntent) -> Unit,
    modifier: Modifier,
) {
    TaskBSortHeader(
        label = label,
        isActive = state.sort == sort,
        reverseSort = state.reverseSort,
        onClick = { onIntent(GroupsIntent.Sort(sort = sort)) },
        modifier = modifier.fillMaxHeight(),
    )
}

@Composable
private fun GroupRow(group: Group, onClick: () -> Unit) {
    val colors = TaskBTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(color = colors.surface)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(weight = GroupNameWeight)
                .padding(start = GroupRowStartPadding, end = GroupNamePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = GroupNamePadding),
        ) {
            Icon(
                imageVector = TaskBIcons.FolderFilled,
                contentDescription = null,
                tint = group.color.color,
                modifier = Modifier.size(size = GroupIconSize),
            )
            Text(
                text = group.name,
                color = colors.text,
                style = TaskBTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = group.tasks.planned.toString(),
            modifier = Modifier
                .weight(weight = GroupCountWeight)
                .padding(start = GroupCountPadding),
            color = colors.muted,
            style = TaskBTheme.typography.body,
            textAlign = TextAlign.Start,
        )
        Text(
            text = group.eventCount.toString(),
            modifier = Modifier
                .weight(weight = GroupCountWeight)
                .padding(start = GroupCountPadding),
            color = colors.muted,
            style = TaskBTheme.typography.body,
            textAlign = TextAlign.Start,
        )
    }
}
