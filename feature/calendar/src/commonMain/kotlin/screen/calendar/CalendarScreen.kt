package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.component.CalendarWorkspace
import com.mkulikov.taskb.feature.item.api.ItemEditorRoute
import com.mkulikov.taskb.feature.item.api.model.TaskFilter
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.dialog.TaskBConfirmationDialog
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

@Composable
internal fun CalendarScreen(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateItem: (ItemEditorRoute) -> Unit,
    onOpenItem: (CalendarItem) -> Unit,
) {
    val query = rememberTextFieldState()
    var filter by rememberSaveable { mutableStateOf<TaskFilter?>(value = null) }
    var groupId by rememberSaveable { mutableStateOf<Int?>(value = null) }
    var showFilters by rememberSaveable { mutableStateOf(value = false) }
    if (state.isContentLoading) {
        TaskBLoading(modifier = Modifier.background(color = TaskBTheme.colors.surface))
        return
    }

    val group = state.groups.find { group -> group.id == groupId }
    val activeGroupId = if (groupId == noGroupFilterId) groupId else group?.id
    val calendarAction: (CalendarIntent) -> Unit = { intent ->
        when (intent) {
            is CalendarIntent.SelectDay -> {
                filter = null
                groupId = null
            }
            CalendarIntent.Today, is CalendarIntent.FilterByDate -> filter = null
            else -> Unit
        }
        onIntent(intent)
    }
    val createItem = {
        val date = state.selectedDate ?: when (filter) {
            TaskFilter.Today -> state.today
            TaskFilter.Tomorrow -> state.today.plus(value = 1, unit = DateTimeUnit.DAY)
            else -> null
        }
        onCreateItem(
            ItemEditorRoute(
                initialDate = date,
                initialGroupId = group?.id,
                useLastSelectedGroup = activeGroupId == null,
            )
        )
    }
    CalendarWorkspace(state = state, onIntent = onIntent) { drag ->
        Column(modifier = Modifier.fillMaxSize().testTag(tag = "calendar-screen")) {
            CalendarTopBar(
                state = state,
                onIntent = calendarAction,
                onOpenGroups = onOpenGroups,
                onOpenSettings = onOpenSettings,
            )
            Row(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                CalendarSidebar(
                    state = state,
                    query = query,
                    filter = filter,
                    groupId = activeGroupId,
                    group = group,
                    drag = drag,
                    onIntent = calendarAction,
                    onFilterByGroup = { id -> groupId = id },
                    onOpenItem = onOpenItem,
                    onOpenFilters = { showFilters = true },
                    onClearFilter = { filter = null },
                    onClearGroup = { groupId = null },
                    onCreateItem = createItem,
                    modifier = Modifier
                        .width(width = 380.dp)
                        .fillMaxHeight(),
                )
                Box(modifier = Modifier.weight(weight = 1f).fillMaxHeight()) {
                    if (state.loadError != null) {
                        TaskBLoadError(
                            message = state.loadError,
                            onRetry = { onIntent(CalendarIntent.RetryLoad) },
                        )
                    } else {
                        CalendarMonthGrid(
                            state = state,
                            drag = drag,
                            onIntent = calendarAction,
                            onOpenTask = onOpenItem,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
    if (showFilters) {
        CalendarFiltersDialog(
            groups = state.groups,
            groupCounts = state.directoryTasks().countTasksByGroup(),
            filter = filter,
            groupId = activeGroupId,
            onDismiss = { showFilters = false },
            onApply = { selectedFilter, selectedGroup ->
                filter = selectedFilter
                groupId = selectedGroup
                if (selectedFilter != null) onIntent(CalendarIntent.ShowUnscheduled)
                showFilters = false
            },
        )
    }
    val task = state.quickDeleteConfirmation
    val date = task?.dueDate
    if (task != null && date != null) {
        val dateLabel = calendarSidebarDateLabel.format(value = date)
        TaskBConfirmationDialog(
            title = "Delete task occurrence?",
            message = "Only the occurrence of “${task.name}” on $dateLabel will be deleted.",
            confirmText = "Delete",
            onConfirm = { onIntent(CalendarIntent.ConfirmQuickDelete) },
            onDismiss = { onIntent(CalendarIntent.DismissQuickDelete) },
        )
    }
}

@Composable
private fun CalendarTopBar(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = TaskBTheme.colors
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(height = 70.dp)) {
        val startPadding = if (maxWidth >= 840.dp) 32.dp else 16.dp
        Row(
            modifier = Modifier.fillMaxSize().padding(start = startPadding, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(weight = 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 24.dp),
            ) {
                Text(
                    text = calendarMonthLabel.format(value = state.month),
                    modifier = Modifier.weight(weight = 1f, fill = false),
                    style = TaskBTheme.typography.display,
                    color = colors.heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CalendarDateNavigation(onIntent = onIntent)
            }
            IconButton(onClick = onOpenGroups, modifier = Modifier.size(size = 48.dp)) {
                Icon(imageVector = TaskBIcons.Folder, contentDescription = "Groups", tint = colors.accent)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(size = 48.dp)) {
                Icon(imageVector = TaskBIcons.Settings, contentDescription = "Settings", tint = colors.accent)
            }
        }
    }
}

@Preview(name = "Calendar – selected day", widthDp = 1900, heightDp = 1180)
@Composable
private fun CalendarScreenPreview() {
    TaskBTheme(darkTheme = false) {
        val state = timedCalendarPreviewState()
        CalendarScreen(
            state = state.copy(taskList = state.tasks),
            onIntent = {},
            onOpenGroups = {},
            onOpenSettings = {},
            onCreateItem = {},
            onOpenItem = {},
        )
    }
}
