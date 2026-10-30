package com.mkulikov.taskb.feature.task.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import com.mkulikov.taskb.feature.task.api.TaskRoute
import com.mkulikov.taskb.feature.task.screen.task.TaskScreen
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorScreen

internal class TaskNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<TaskRoute> {
            TaskScreen(
                onBack = navigator::back,
                onCreateTask = { date, groupId -> navigator.navigate(TaskEditorRoute.Create(date, groupId)) },
                onEditTask = { navigator.navigate(TaskEditorRoute.Edit(it)) },
            )
        }
        scope.entry<TaskEditorRoute.Create> { route ->
            TaskEditorScreen(initialDate = route.initialDate, initialGroupId = route.initialGroupId, onBack = navigator::back)
        }
        scope.entry<TaskEditorRoute.Edit> { route ->
            TaskEditorScreen(taskId = route.taskId, onBack = navigator::back)
        }
    }
}
