package com.mkulikov.taskb.feature.task.screen.item_editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
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
import com.mkulikov.taskb.feature.event.api.EventEditorRoute
import com.mkulikov.taskb.feature.event.api.IEventItemEditor
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
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
internal fun TaskItemEditorScreen(
    onBack: () -> Unit,
    groupResults: Flow<GroupSelectionResult>,
    onChooseGroup: (Int?) -> Unit,
    initialDate: LocalDate? = null,
    initialGroupId: Int? = null,
    initialIsEvent: Boolean = false,
    edit: TaskEditorRoute? = null,
) {
    val repository = koinInject<ITaskRepository>()
    val groups = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource(key = TaskEditorStore::class) {
        TaskEditorStore(
            repository = repository,
            groups = groups,
            taskId = edit?.taskId,
            occurrenceId = edit?.occurrenceId,
            initialDate = initialDate,
            initialGroup = initialGroupId,
        )
    }
    val state by store.state.collectAsState()
    var isEvent by rememberSaveable { mutableStateOf(value = initialIsEvent) }
    val changeType: (Boolean) -> Unit = { eventSelected -> isEvent = eventSelected }
    LaunchedEffect(key1 = store, key2 = state.isFinished) {
        if (state.isFinished) onBack()
    }
    LaunchedEffect(key1 = store, key2 = groupResults) {
        groupResults.collect { result -> store.accept(intent = TaskEditorIntent.GroupChanged(id = result.groupId)) }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        BoxWithConstraints {
            val availableHeight = maxHeight
            if (edit == null && isEvent) {
                val fields = state.itemEditorFields()
                val editor = koinInject<IEventItemEditor>()
                editor.Content(
                    name = state.name,
                    description = state.description,
                    initialDate = state.dueDate,
                    groupId = state.groupId,
                    availableHeight = availableHeight,
                    onFinished = onBack,
                ) { event ->
                    ItemEditorContent(
                        fields = fields.copy(
                            isLoading = fields.isLoading || event.isLoading,
                            loadError = fields.loadError ?: event.loadError,
                            enabled = fields.enabled && event.enabled,
                        ),
                        isEvent = true,
                        isSaving = event.isSaving,
                        canSave = event.canSave && fields.enabled,
                        onTypeChanged = changeType,
                        onSave = event.onSave,
                        onBack = onBack,
                        onChooseGroup = { onChooseGroup(state.groupId) },
                        onClearGroup = { store.accept(intent = TaskEditorIntent.GroupChanged(id = null)) },
                        onRetry = {
                            store.accept(intent = TaskEditorIntent.RetryGroups)
                            event.onRetry()
                        },
                        schedule = event.schedule,
                    )
                }
            } else {
                TaskItemEditorContent(
                    state = state,
                    onIntent = store::accept,
                    onBack = onBack,
                    onChooseGroup = { onChooseGroup(state.groupId) },
                    onTypeChanged = changeType.takeIf { edit == null },
                )
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
internal fun TaskItemEditorContent(
    state: TaskEditorState,
    onIntent: (TaskEditorIntent) -> Unit,
    onBack: () -> Unit,
    onChooseGroup: () -> Unit,
    onTypeChanged: ((Boolean) -> Unit)? = null,
) {
    val delete = { onIntent(TaskEditorIntent.PrepareDelete) }
    ItemEditorContent(
        fields = state.itemEditorFields(),
        isEvent = false,
        isSaving = state.isSaving,
        canSave = state.canSave,
        onTypeChanged = onTypeChanged,
        onDelete = delete.takeUnless { state.isNew },
        onSave = { onIntent(TaskEditorIntent.Save) },
        onBack = onBack,
        onChooseGroup = onChooseGroup,
        onClearGroup = { onIntent(TaskEditorIntent.GroupChanged(id = null)) },
        onRetry = {
            val intent = if (state.loadError != null) TaskEditorIntent.RetryLoad else TaskEditorIntent.RetryGroups
            onIntent(intent)
        },
    ) {
        TaskItemEditorSchedule(state = state, onIntent = onIntent)
    }
}

private fun TaskEditorState.itemEditorFields(): ItemEditorFields {
    val error = loadError ?: groupsLoadError
    return ItemEditorFields(
        name = name,
        description = description,
        group = groups.find { group -> group.id == groupId },
        isLoading = isLoading || isLoadingGroups,
        loadError = error,
        enabled = !isBusy && !isFinished && dialog == null && error == null,
    )
}

@Composable
internal fun EventItemEditorScreen(
    edit: EventEditorRoute,
    onBack: () -> Unit,
    groupResults: Flow<GroupSelectionResult>,
    onChooseGroup: (Int?) -> Unit,
) {
    val editor = koinInject<IEventItemEditor>()
    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        BoxWithConstraints {
            editor.Content(
                availableHeight = maxHeight,
                onFinished = onBack,
                edit = edit,
                groupResults = groupResults,
            ) { event ->
                ItemEditorContent(
                    fields = ItemEditorFields(
                        name = event.name,
                        description = event.description,
                        group = event.group,
                        isLoading = event.isLoading,
                        loadError = event.loadError,
                        enabled = event.enabled,
                    ),
                    isEvent = true,
                    isSaving = event.isSaving,
                    canSave = event.canSave,
                    onDelete = event.onDelete,
                    onSave = event.onSave,
                    onBack = onBack,
                    onChooseGroup = { onChooseGroup(event.group?.id) },
                    onClearGroup = event.onClearGroup,
                    onRetry = event.onRetry,
                    schedule = event.schedule,
                )
            }
        }
    }
}

private data class ItemEditorFields(
    val name: TextFieldState,
    val description: TextFieldState,
    val group: Group? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val enabled: Boolean = true,
)

@Composable
private fun ItemEditorContent(
    fields: ItemEditorFields,
    isEvent: Boolean,
    isSaving: Boolean,
    canSave: Boolean,
    onTypeChanged: ((Boolean) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onChooseGroup: () -> Unit,
    onClearGroup: () -> Unit,
    onRetry: () -> Unit,
    schedule: @Composable () -> Unit,
) {
    val editing = onDelete != null
    val enabled = fields.enabled
    Column(modifier = Modifier.fillMaxSize().testTag(tag = if (editing) "item-editor" else "task-creation")) {
        Box {
            TaskBEditorTopAppBar(
                title = if (editing) "Edit item" else "New item",
                onBack = onBack,
                backEnabled = !isSaving,
                height = 70.dp,
            ) {
                if (onDelete != null) {
                    TaskBEditorButton(
                        label = "Delete",
                        icon = TaskBIcons.Delete,
                        onClick = onDelete,
                        enabled = enabled,
                        destructive = true,
                        modifier = Modifier.widthIn(min = 96.dp),
                    )
                }
                TaskBEditorButton(
                    label = if (editing) "Save changes" else "Create item",
                    icon = if (editing) TaskBIcons.Done else TaskBIcons.Add,
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.widthIn(min = if (editing) 150.dp else 135.dp),
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
            val error = fields.loadError
            when {
                fields.isLoading -> TaskBLoading()
                error != null -> TaskBLoadError(
                    message = error,
                    onRetry = onRetry,
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
                            ItemEditorLabel(text = name)
                            TaskBTextField(
                                state = fields.name,
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
                                group = fields.group,
                                onClearGroup = onClearGroup.takeIf { fields.group != null },
                                onChooseGroup = onChooseGroup,
                                enabled = enabled,
                                description = "Choose group",
                                clearDescription = "Clear group",
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            ItemEditorLabel(text = "Description")
                            TaskBTextArea(
                                state = fields.description,
                                description = if (isEvent) "Event description" else "Task description",
                                placeholder = "Add a description",
                                enabled = enabled,
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            ItemEditorLabel(text = "Type")
                            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                                TaskBSelectionButton(
                                    label = "Task",
                                    selected = !isEvent,
                                    enabled = enabled && onTypeChanged != null,
                                    modifier = Modifier.weight(weight = 1f),
                                    onClick = { onTypeChanged?.invoke(false) },
                                )
                                TaskBSelectionButton(
                                    label = "Event",
                                    selected = isEvent,
                                    enabled = enabled && onTypeChanged != null,
                                    modifier = Modifier.weight(weight = 1f),
                                    onClick = { onTypeChanged?.invoke(true) },
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
internal fun ItemEditorLabel(text: String) {
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
        ItemEditorContent(
            fields = ItemEditorFields(name = state.name, description = state.description),
            isEvent = false,
            isSaving = false,
            canSave = false,
            onTypeChanged = {},
            onSave = {},
            onBack = {},
            onChooseGroup = {},
            onClearGroup = {},
            onRetry = {},
        ) {
            TaskItemEditorSchedule(state = state, onIntent = {})
        }
    }
}
