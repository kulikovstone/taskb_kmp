package com.mkulikov.taskb.feature.task.screen.task_creation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.event.api.IEventCreationEditor
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorDialogs
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorIntent
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorState
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskEditorStore
import com.mkulikov.taskb.feature.task.screen.task_editor.TaskGroupSection
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.component.TaskBEditorTopAppBar
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.component.TaskBTextArea
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject

@Composable
internal fun TaskCreationScreen(
    initialDate: LocalDate?,
    initialGroupId: Int?,
    onBack: () -> Unit,
    groupResults: Flow<GroupSelectionResult>,
    onChooseGroup: (Int?) -> Unit,
) {
    val repository = koinInject<ITaskRepository>()
    val groups = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource(key = TaskEditorStore::class) {
        TaskEditorStore(
            repository = repository,
            groups = groups,
            initialDate = initialDate,
            initialGroup = initialGroupId,
        )
    }
    val state by store.state.collectAsState()
    var isEvent by rememberSaveable { mutableStateOf(value = false) }
    LaunchedEffect(key1 = store, key2 = state.isFinished) {
        if (state.isFinished) onBack()
    }
    LaunchedEffect(key1 = store, key2 = groupResults) {
        groupResults.collect { result -> store.accept(intent = TaskEditorIntent.GroupChanged(id = result.groupId)) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        BoxWithConstraints {
            val availableHeight = maxHeight
            if (isEvent) {
                val editor = koinInject<IEventCreationEditor>()
                editor.Content(
                    name = state.name,
                    description = state.description,
                    initialDate = state.dueDate,
                    groupId = state.groupId,
                    availableHeight = availableHeight,
                    onSaved = onBack,
                ) { event ->
                    TaskCreationContent(
                        state = state,
                        isEvent = true,
                        isSaving = event.isSaving,
                        canSave = event.canSave && !state.isBusy && state.groupsLoadError == null,
                        enabled = event.enabled && !state.isBusy,
                        onTypeChanged = { eventSelected -> isEvent = eventSelected },
                        onSave = event.onSave,
                        onBack = onBack,
                        onChooseGroup = { onChooseGroup(state.groupId) },
                        onTaskIntent = store::accept,
                        schedule = event.schedule,
                    )
                }
            } else {
                TaskCreationContent(
                    state = state,
                    isEvent = false,
                    isSaving = state.isSaving,
                    canSave = state.canSave,
                    enabled = !state.isBusy && !state.isFinished && state.dialog == null,
                    onTypeChanged = { eventSelected -> isEvent = eventSelected },
                    onSave = { store.accept(intent = TaskEditorIntent.Save) },
                    onBack = onBack,
                    onChooseGroup = { onChooseGroup(state.groupId) },
                    onTaskIntent = store::accept,
                ) {
                    TaskCreationSchedule(state = state, onIntent = store::accept)
                }
                TaskEditorDialogs(
                    state = state,
                    today = state.today,
                    onIntent = store::accept,
                    availableHeight = availableHeight,
                )
            }
        }
    }
}

@Composable
private fun TaskCreationContent(
    state: TaskEditorState,
    isEvent: Boolean,
    isSaving: Boolean,
    canSave: Boolean,
    enabled: Boolean,
    onTypeChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onChooseGroup: () -> Unit,
    onTaskIntent: (TaskEditorIntent) -> Unit,
    schedule: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().testTag(tag = "task-creation")) {
        Box {
            TaskBEditorTopAppBar(title = "New item", onBack = onBack, backEnabled = !isSaving, height = 70.dp) {
                TaskBEditorButton(
                    label = "Create item",
                    icon = TaskBIcons.Add,
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.widthIn(min = 135.dp),
                )
            }
            Divider(modifier = Modifier.align(alignment = Alignment.BottomCenter), color = TaskBTheme.colors.border)
        }
        Box(
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val error = state.groupsLoadError
            when {
                state.isLoadingGroups -> TaskBLoading()
                error != null -> TaskBLoadError(
                    message = error,
                    onRetry = { onTaskIntent(TaskEditorIntent.RetryGroups) },
                )
                else -> Column(
                    modifier = Modifier
                        .widthIn(max = 724.dp)
                        .fillMaxWidth()
                        .verticalScroll(state = rememberScrollState())
                        .padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(space = 24.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            val name = if (isEvent) "Event name" else "Task name"
                            CreationFieldLabel(text = name)
                            TaskBTextField(
                                state = state.name,
                                description = name,
                                placeholder = if (isEvent) "Enter event name" else "Enter task name",
                                enabled = enabled,
                                autoFocus = true,
                                onDone = { if (canSave) onSave() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            TaskGroupSection(
                                state = state,
                                groups = state.groups,
                                onIntent = onTaskIntent,
                                onChooseGroup = onChooseGroup,
                                enabled = enabled,
                                description = "Choose group",
                                clearDescription = "Clear group",
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            CreationFieldLabel(text = "Description")
                            TaskBTextArea(
                                state = state.description,
                                description = if (isEvent) "Event description" else "Task description",
                                placeholder = "Add a description",
                                enabled = enabled,
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            CreationFieldLabel(text = "Type")
                            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                                TaskBSelectionButton(
                                    label = "Task",
                                    selected = !isEvent,
                                    enabled = enabled,
                                    modifier = Modifier.weight(weight = 1f),
                                    onClick = { onTypeChanged(false) },
                                )
                                TaskBSelectionButton(
                                    label = "Event",
                                    selected = isEvent,
                                    enabled = enabled,
                                    modifier = Modifier.weight(weight = 1f),
                                    onClick = { onTypeChanged(true) },
                                )
                            }
                        }
                    }
                    schedule()
                }
            }
        }
    }
}

@Composable
internal fun CreationFieldLabel(text: String) {
    Text(
        text = text,
        style = TaskBTheme.typography.label,
        color = TaskBTheme.colors.text,
        modifier = Modifier.height(height = 18.dp),
    )
    Spacer(modifier = Modifier.height(height = 8.dp))
}

@Preview(name = "Task creation", widthDp = 1900, heightDp = 1180)
@Composable
private fun TaskCreationPreview() {
    val state = TaskEditorState(isLoadingGroups = false)
    TaskBTheme(darkTheme = false) {
        TaskCreationContent(
            state = state,
            isEvent = false,
            isSaving = false,
            canSave = false,
            enabled = true,
            onTypeChanged = {},
            onSave = {},
            onBack = {},
            onChooseGroup = {},
            onTaskIntent = {},
        ) {
            TaskCreationSchedule(state = state, onIntent = {})
        }
    }
}
