package com.mkulikov.taskb.feature.task.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.GroupSelectionRoute
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import com.mkulikov.taskb.feature.task.api.TaskRoute
import com.mkulikov.taskb.feature.task.screen.task.TaskScreen
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorScreen

internal class TaskNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<TaskRoute> {
            val groups = rememberNavEntryResource("group-result") { navigator.createResultReceiver<GroupSelectionResult>() }
            TaskScreen(
                onBack = navigator::back,
                onCreateTask = { date, groupId -> navigator.navigate(TaskEditorRoute.Create(date, groupId)) },
                onEditTask = { navigator.navigate(TaskEditorRoute.Edit(it)) },
                onOpenOccurrence = { navigator.navigate(TaskEditorRoute.Edit(it.id, it.occurrenceId)) },
                groupResults = groups.results,
                onChooseGroup = { selected, noGroup ->
                    navigator.navigate(GroupSelectionRoute(groups.key, selected, showNoGroup = true, noGroupSelected = noGroup))
                },
            )
        }
        scope.entry<TaskEditorRoute.Create> { route ->
            val groups = rememberNavEntryResource("group-result") { navigator.createResultReceiver<GroupSelectionResult>() }
            TaskEditorScreen(initialDate = route.initialDate, initialGroupId = route.initialGroupId, onBack = navigator::back,
                groupResults = groups.results,
                onChooseGroup = { navigator.navigate(GroupSelectionRoute(groups.key, it, showNoGroup = false)) })
        }
        scope.entry<TaskEditorRoute.Edit> { route ->
            val groups = rememberNavEntryResource("group-result") { navigator.createResultReceiver<GroupSelectionResult>() }
            TaskEditorScreen(taskId = route.taskId, occurrenceId = route.occurrenceId, onBack = navigator::back,
                groupResults = groups.results,
                onChooseGroup = { navigator.navigate(GroupSelectionRoute(groups.key, it, showNoGroup = false)) })
        }
    }
}
