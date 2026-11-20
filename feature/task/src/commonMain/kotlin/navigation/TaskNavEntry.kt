package com.mkulikov.taskb.feature.task.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.GroupSelectionRoute
import com.mkulikov.taskb.feature.task.api.TaskCreationRoute
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import com.mkulikov.taskb.feature.task.api.TaskRoute
import com.mkulikov.taskb.feature.task.api.model.TaskFilter
import com.mkulikov.taskb.feature.task.screen.task.TaskScreen
import com.mkulikov.taskb.feature.task.screen.task_creation.TaskCreationScreen
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorScreen
import kotlinx.coroutines.flow.MutableStateFlow

internal class TaskNavEntry : INavEntry {
    private val calendarTaskGroupId = MutableStateFlow<Int?>(value = null)

    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<TaskRoute> {
            TaskList(navigator = navigator)
        }
        scope.entry<TaskRoute.NoDate> {
            TaskList(navigator = navigator, initialFilter = TaskFilter.NoDate)
        }
        scope.entry<TaskCreationRoute> { route ->
            val groups = rememberNavEntryResource(key = "group-result") {
                navigator.createResultReceiver<GroupSelectionResult>()
            }
            TaskCreationScreen(
                initialDate = route.initialDate,
                initialGroupId = route.initialGroupId,
                onBack = navigator::back,
                groupResults = groups.results,
                onChooseGroup = { groupId ->
                    navigator.navigate(
                        key = GroupSelectionRoute(
                            resultKey = groups.key,
                            selectedGroupId = groupId,
                            showNoGroup = false,
                        )
                    )
                },
            )
        }
        scope.entry<TaskEditorRoute.Create> { route ->
            val groups = rememberNavEntryResource(key = "group-result") {
                navigator.createResultReceiver<GroupSelectionResult>()
            }
            TaskEditorScreen(
                initialDate = route.initialDate,
                initialGroupId = route.initialGroupId
                    ?: if (route.rememberGroupSelection) calendarTaskGroupId.value else null,
                onBack = navigator::back,
                groupResults = groups.results,
                onGroupSelected = { groupId -> if (route.rememberGroupSelection) calendarTaskGroupId.value = groupId },
                onChooseGroup = { groupId ->
                    navigator.navigate(
                        key = GroupSelectionRoute(
                            resultKey = groups.key,
                            selectedGroupId = groupId,
                            showNoGroup = false,
                        )
                    )
                },
            )
        }
        scope.entry<TaskEditorRoute.Edit> { route ->
            val groups = rememberNavEntryResource(key = "group-result") {
                navigator.createResultReceiver<GroupSelectionResult>()
            }
            TaskEditorScreen(
                taskId = route.taskId,
                occurrenceId = route.occurrenceId,
                onBack = navigator::back,
                groupResults = groups.results,
                onChooseGroup = { groupId ->
                    navigator.navigate(
                        key = GroupSelectionRoute(
                            resultKey = groups.key,
                            selectedGroupId = groupId,
                            showNoGroup = false,
                        )
                    )
                },
            )
        }
    }

    @Composable
    private fun TaskList(navigator: IAppNavigator, initialFilter: TaskFilter? = null) {
        val groups = rememberNavEntryResource(key = "group-result") {
            navigator.createResultReceiver<GroupSelectionResult>()
        }
        TaskScreen(
            onBack = navigator::back,
            onCreateTask = { date, groupId ->
                navigator.navigate(key = TaskEditorRoute.Create(initialDate = date, initialGroupId = groupId))
            },
            onEditTask = { taskId -> navigator.navigate(key = TaskEditorRoute.Edit(taskId = taskId)) },
            onOpenOccurrence = { task ->
                navigator.navigate(key = TaskEditorRoute.Edit(taskId = task.id, occurrenceId = task.occurrenceId))
            },
            groupResults = groups.results,
            onChooseGroup = { selected, noGroup ->
                navigator.navigate(
                    key = GroupSelectionRoute(
                        resultKey = groups.key,
                        selectedGroupId = selected,
                        showNoGroup = true,
                        noGroupSelected = noGroup,
                    )
                )
            },
            initialFilter = initialFilter,
        )
    }
}
