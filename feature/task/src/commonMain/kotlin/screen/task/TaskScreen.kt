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
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.ui.dialog.TaskBMessageDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.koin.compose.koinInject

@Composable
internal fun TaskScreen(
    onBack: () -> Unit,
    onCreateTask: (LocalDate?, Int?) -> Unit,
    onEditTask: (Int) -> Unit,
    groupResults: Flow<GroupSelectionResult>,
    onChooseGroup: (Int?, Boolean) -> Unit,
    onOpenOccurrence: ((Task) -> Unit)? = null,
) {
    val repository = koinInject<ITaskRepository>()
    val groups = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource(key = "tasks") { TaskStore(repository = repository, groups = groups) }
    val state by store.state.collectAsState()
    LaunchedEffect(key1 = store, key2 = groupResults) {
        groupResults.collect { result ->
            store.accept(
                intent = TaskIntent.FilterGroup(
                    filter = result.groupId?.let(block = TaskGroupFilter::Selected) ?: TaskGroupFilter.NoGroup
                )
            )
        }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            if (!wide && state.selection != null) {
                TaskSelectionScreen(state = state, onIntent = store::accept)
            } else {
                TaskListScreen(
                    state = state,
                    onIntent = store::accept,
                    wide = wide,
                    onBack = onBack,
                    onChooseGroup = {
                        onChooseGroup(
                            (state.groupFilter as? TaskGroupFilter.Selected)?.id,
                            state.groupFilter == TaskGroupFilter.NoGroup,
                        )
                    },
                    onNewTask = {
                        val date = when (state.filter) {
                            TaskFilter.Today -> state.today
                            TaskFilter.Tomorrow -> state.today.plus(value = 1, unit = DateTimeUnit.DAY)
                            else -> null
                        }
                        onCreateTask(date, (state.groupFilter as? TaskGroupFilter.Selected)?.id)
                    },
                    onOpenTask = onEditTask,
                    onOpenOccurrence = onOpenOccurrence,
                )
            }
            if (wide && state.selection != null)
                TaskSelectionDialog(state = state, onIntent = store::accept, availableHeight = maxHeight)
        }
    }
    state.error?.let { message ->
        TaskBMessageDialog(title = "Couldn't update task", message = message) {
            store.accept(intent = TaskIntent.DismissError)
        }
    }
}
