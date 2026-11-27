package com.mkulikov.taskb.feature.item.screen.item_editor

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.item.api.ItemEditorRoute
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.feature.item.api.repository.IEventRepository
import com.mkulikov.taskb.feature.item.api.repository.ITaskRepository
import com.mkulikov.taskb.feature.settings.api.IItemGroupSettings
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
import org.koin.compose.koinInject

@Composable
internal fun ItemEditorScreen(
    route: ItemEditorRoute,
    onBack: () -> Unit,
    groupResults: Flow<GroupSelectionResult>,
    onChooseGroup: (Int?) -> Unit,
) {
    val tasks = koinInject<ITaskRepository>()
    val events = koinInject<IEventRepository>()
    val groups = koinInject<IGroupRepository>()
    val groupSettings = koinInject<IItemGroupSettings>()
    val store = rememberNavEntryResource(key = ItemEditorStore::class) {
        ItemEditorStore(
            tasks = tasks,
            events = events,
            groups = groups,
            route = route,
            lastSelectedGroupId = groupSettings::lastSelectedGroupId,
        )
    }
    val state by store.state.collectAsState()
    LaunchedEffect(key1 = store, key2 = state.isFinished) {
        if (state.isFinished) onBack()
    }
    LaunchedEffect(key1 = store, key2 = groupResults) {
        groupResults.collect { result -> store.accept(intent = ItemEditorIntent.GroupChanged(id = result.groupId)) }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
        BoxWithConstraints {
            ItemEditorContent(
                state = state,
                onIntent = store::accept,
                onBack = onBack,
                onChooseGroup = { onChooseGroup(state.groupId) },
            )
            ItemEditorDialogs(state = state, onIntent = store::accept, availableHeight = maxHeight)
        }
    }
}

@Composable
private fun ItemEditorContent(
    state: ItemEditorState,
    onIntent: (ItemEditorIntent) -> Unit,
    onBack: () -> Unit,
    onChooseGroup: () -> Unit,
) {
    val editing = !state.isNew
    val enabled = state.enabled
    val type = state.type
    Column(modifier = Modifier.fillMaxSize().testTag(tag = if (editing) "item-editor" else "task-creation")) {
        Box {
            TaskBEditorTopAppBar(
                title = if (editing) "Edit item" else "New item",
                onBack = onBack,
                backEnabled = !state.isSaving,
                height = 70.dp,
            ) {
                if (editing) {
                    TaskBEditorButton(
                        label = "Delete",
                        icon = TaskBIcons.Delete,
                        onClick = { onIntent(ItemEditorIntent.PrepareDelete) },
                        enabled = enabled,
                        destructive = true,
                        modifier = Modifier.widthIn(min = 96.dp),
                    )
                }
                TaskBEditorButton(
                    label = if (editing) "Save changes" else "Create item",
                    icon = if (editing) TaskBIcons.Done else TaskBIcons.Add,
                    onClick = { onIntent(ItemEditorIntent.Save) },
                    enabled = state.canSave,
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
            val error = state.loadError ?: state.groupsLoadError
            when {
                state.isLoading || state.isLoadingGroups -> TaskBLoading()
                error != null -> TaskBLoadError(
                    message = error,
                    onRetry = {
                        val retry = if (state.loadError != null) {
                            ItemEditorIntent.RetryLoad
                        } else {
                            ItemEditorIntent.RetryGroups
                        }
                        onIntent(retry)
                    },
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
                            val name = "${type.name} name"
                            ItemEditorLabel(text = name)
                            TaskBTextField(
                                state = state.name,
                                description = name,
                                placeholder = "Enter ${type.name.lowercase()} name",
                                enabled = enabled,
                                autoFocus = true,
                                onDone = { if (state.canSave) onIntent(ItemEditorIntent.Save) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            val group = state.groups.find { group -> group.id == state.groupId }
                            ItemGroupField(
                                group = group,
                                onClearGroup = group?.let { { onIntent(ItemEditorIntent.GroupChanged(id = null)) } },
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
                                state = state.description,
                                description = "${type.name} description",
                                placeholder = "Add a description",
                                enabled = enabled,
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            ItemEditorLabel(text = "Type")
                            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                                val visibleTypes = if (editing) listOf(element = type) else ItemType.entries
                                visibleTypes.forEach { option ->
                                    TaskBSelectionButton(
                                        label = option.name,
                                        selected = type == option,
                                        enabled = enabled && state.isNew,
                                        modifier = Modifier.weight(weight = 1f),
                                        onClick = { onIntent(ItemEditorIntent.SelectType(type = option)) },
                                    )
                                }
                            }
                        }
                    }
                    when (val details = state.schedule.details) {
                        is TaskSchedule -> TaskItemEditorSchedule(state = state, details = details, onIntent = onIntent)
                        is EventSchedule -> EventItemEditorSchedule(
                            state = state,
                            details = details,
                            onIntent = onIntent,
                        )
                    }
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
    TaskBTheme(darkTheme = false) {
        ItemEditorContent(
            state = ItemEditorState(isLoadingGroups = false),
            onIntent = {},
            onBack = {},
            onChooseGroup = {},
        )
    }
}
