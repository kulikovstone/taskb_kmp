package com.mkulikov.taskb.feature.calendar.screen.calendar_two

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
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarDateNavigation
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarIntent
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarItem
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarMonthGrid
import com.mkulikov.taskb.feature.calendar.screen.calendar.CalendarState
import com.mkulikov.taskb.feature.calendar.screen.calendar.calendarMonthLabel
import com.mkulikov.taskb.feature.calendar.screen.calendar.timedCalendarPreviewState
import com.mkulikov.taskb.feature.task.api.model.TaskFilter
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun Calendar2Screen(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateItem: (LocalDate?, Int?) -> Unit,
    onOpenItem: (CalendarItem) -> Unit,
) {
    val query = rememberTextFieldState()
    var filter by rememberSaveable { mutableStateOf<TaskFilter?>(value = null) }
    var groupId by rememberSaveable { mutableStateOf<Int?>(value = null) }
    var showFilters by rememberSaveable { mutableStateOf(value = false) }
    val group = state.groups.find { group -> group.id == groupId }
    val activeGroupId = if (groupId == noGroupFilterId) groupId else group?.id
    val calendarAction: (CalendarIntent) -> Unit = { intent ->
        when (intent) {
            is CalendarIntent.SelectDay -> {
                filter = null
                groupId = null
            }
            CalendarIntent.Today -> filter = null
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
        onCreateItem(date, group?.id)
    }
    CalendarWorkspace(state = state, onIntent = onIntent) { drag ->
        Column(modifier = Modifier.fillMaxSize().testTag(tag = "calendar-2")) {
            Calendar2TopBar(
                state = state,
                onIntent = calendarAction,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
            )
            Row(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
                Calendar2Sidebar(
                    state = state,
                    query = query,
                    filter = filter,
                    groupId = activeGroupId,
                    group = group,
                    drag = drag,
                    onIntent = onIntent,
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
                    when {
                        state.isLoading -> TaskBLoading()
                        state.loadError != null -> TaskBLoadError(
                            message = state.loadError,
                            onRetry = { onIntent(CalendarIntent.RetryLoad) },
                        )
                        else -> CalendarMonthGrid(
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
        Calendar2FiltersDialog(
            groups = state.groups,
            groupCounts = state.directoryTasks().groupingBy { task -> task.groupId }.eachCount(),
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
}

@Composable
private fun Calendar2TopBar(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = TaskBTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 70.dp)
            .padding(start = 16.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(size = 48.dp)) {
            Icon(imageVector = TaskBIcons.Back, contentDescription = "Back", tint = colors.heading)
        }
        Row(
            modifier = Modifier.weight(weight = 1f).padding(start = 8.dp),
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
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(size = 48.dp)) {
            Icon(imageVector = TaskBIcons.Settings, contentDescription = "Settings", tint = colors.accent)
        }
    }
}

@Preview(name = "Calendar 2 – selected day", widthDp = 1900, heightDp = 1180)
@Composable
private fun Calendar2ScreenPreview() {
    TaskBTheme(darkTheme = false) {
        val state = timedCalendarPreviewState()
        Calendar2Screen(
            state = state.copy(taskList = state.tasks),
            onIntent = {},
            onBack = {},
            onOpenSettings = {},
            onCreateItem = { _, _ -> },
            onOpenItem = {},
        )
    }
}
