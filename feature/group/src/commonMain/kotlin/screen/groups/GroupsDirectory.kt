package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBSortHeader
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

private val GroupRowHorizontalPadding = 20.dp
private val GroupMarkSize = 28.dp
private val GroupRowSpacing = 12.dp
private val GroupSortHeaderPadding = 16.dp

@Composable
internal fun GroupsFilters(state: GroupsState, onIntent: (GroupsIntent) -> Unit) {
    val colors = TaskBTheme.colors
    Row(
        modifier = Modifier.horizontalScroll(state = rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        listOf(
                GroupFilter.All to "All  ${state.groups.size}",
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
    compact: Boolean,
    modifier: Modifier = Modifier,
    onOpenGroup: (Int) -> Unit,
) {
    val colors = TaskBTheme.colors
    val groups = state.visibleGroups
    Column(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxWidth().height(height = if (compact) 28.dp else 40.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = state.summary,
                color = colors.muted,
                style = TaskBTheme.typography.section.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                ),
            )
        }
        if (compact) {
            Spacer(modifier = Modifier.height(height = 16.dp))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = GroupRowHorizontalPadding + GroupMarkSize + GroupRowSpacing -
                            GroupSortHeaderPadding,
                        end = GroupRowHorizontalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = GroupRowSpacing),
            ) {
                SortHeader(
                    label = "Name",
                    sort = GroupSort.Name,
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(weight = 1f),
                )
                SortHeader(
                    label = "Tasks",
                    sort = GroupSort.Tasks,
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.width(width = 96.dp),
                    centered = true,
                )
                Spacer(modifier = Modifier.width(width = 20.dp))
            }
            Divider(color = colors.border)
        }
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
            else -> LazyColumn(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                items(items = groups, key = Group::id) { group ->
                    GroupRow(
                        group = group,
                        compact = compact,
                        showDivider = !compact || group.id != groups.last().id,
                    ) {
                        onOpenGroup(group.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortHeader(
    label: String,
    sort: GroupSort,
    state: GroupsState,
    onIntent: (GroupsIntent) -> Unit,
    modifier: Modifier,
    centered: Boolean = false,
) {
    Box(
        modifier = modifier,
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        TaskBSortHeader(
            label = label,
            isActive = state.sort == sort,
            reverseSort = state.reverseSort,
            onClick = { onIntent(GroupsIntent.Sort(sort = sort)) },
            contentPadding = PaddingValues(all = GroupSortHeaderPadding),
        )
    }
}

@Composable
private fun GroupRow(group: Group, compact: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    val colors = TaskBTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = if (showDivider) 63.dp else 64.dp)
                .padding(horizontal = GroupRowHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = GroupRowSpacing),
        ) {
            Box(modifier = Modifier.size(size = GroupMarkSize), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TaskBIcons.FolderFilled,
                    contentDescription = null,
                    tint = group.color.color,
                    modifier = Modifier.size(size = 20.dp),
                )
            }
            Text(
                text = group.name,
                modifier = Modifier.weight(weight = 1f),
                color = colors.text,
                style = TaskBTheme.typography.section,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = if (compact) Modifier else Modifier.width(width = 96.dp),
                contentAlignment = Alignment.Center,
            ) {
                val count = group.tasks.planned
                Text(
                    text = if (compact) "$count ${if (count == 1) "task" else "tasks"}" else count.toString(),
                    color = colors.muted,
                    style = TaskBTheme.typography.body,
                )
            }
            if (!compact)
                Icon(
                    imageVector = TaskBIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(size = 20.dp),
                )
        }
        if (showDivider) Divider(color = colors.border, thickness = 1.dp)
    }
}
