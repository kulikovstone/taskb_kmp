package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.ui.dialog.TaskBMessageDialog
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject

@Composable
internal fun TaskScreen(
    onBack: () -> Unit, onCreateTask: (LocalDate?, Int?) -> Unit, onEditTask: (Int) -> Unit,
    groupResults: Flow<GroupSelectionResult>, onChooseGroup: (Int?, Boolean) -> Unit,
) {
    val repository = koinInject<ITaskRepository>()
    val groups = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource("tasks") { TaskStore(repository, groups) }
    val state by store.state.collectAsState()
    LaunchedEffect(store, groupResults) {
        groupResults.collect { result ->
            store.accept(TaskIntent.FilterGroup(result.groupId?.let(TaskGroupFilter::Selected) ?: TaskGroupFilter.NoGroup))
        }
    }
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            if (!wide && state.selection != null) TaskSelectionScreen(state, store::accept)
            else TaskListScreen(state, store::accept, wide, onBack, onChooseGroup = {
                onChooseGroup((state.groupFilter as? TaskGroupFilter.Selected)?.id, state.groupFilter == TaskGroupFilter.NoGroup)
            }, onNewTask = {
                val date = when (state.filter) {
                    TaskFilter.Today -> state.today
                    TaskFilter.Tomorrow -> state.today.plus(1, DateTimeUnit.DAY)
                    else -> null
                }
                onCreateTask(date, (state.groupFilter as? TaskGroupFilter.Selected)?.id)
            }, onOpenTask = onEditTask)
            if (wide && state.selection != null) TaskSelectionDialog(state, store::accept, maxHeight)
        }
    }
    state.error?.let { TaskBMessageDialog("Couldn't update task", it) { store.accept(TaskIntent.DismissError) } }
}
