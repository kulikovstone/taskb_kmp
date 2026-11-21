package com.mkulikov.taskb.feature.event.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.event.api.EventEditorRoute
import com.mkulikov.taskb.feature.event.api.EventItemEditorForm
import com.mkulikov.taskb.feature.event.api.IEventItemEditor
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.event.screen.event_editor.EventDateSection
import com.mkulikov.taskb.feature.event.screen.event_editor.EventEditorDialogs
import com.mkulikov.taskb.feature.event.screen.event_editor.EventEditorIntent
import com.mkulikov.taskb.feature.event.screen.event_editor.EventEditorState
import com.mkulikov.taskb.feature.event.screen.event_editor.EventEditorStore
import com.mkulikov.taskb.feature.event.screen.event_editor.EventEndTimeField
import com.mkulikov.taskb.feature.event.screen.event_editor.EventRepeatEnd
import com.mkulikov.taskb.feature.event.screen.event_editor.EventRepeatOn
import com.mkulikov.taskb.feature.event.screen.event_editor.EventRepeatPattern
import com.mkulikov.taskb.feature.event.screen.event_editor.EventStartTimeField
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

internal class EventItemEditor(
    private val repository: IEventRepository,
    private val groups: IGroupRepository,
) : IEventItemEditor {
    @Composable
    override fun Content(
        availableHeight: Dp,
        onFinished: () -> Unit,
        name: TextFieldState?,
        description: TextFieldState?,
        initialDate: LocalDate?,
        groupId: Int?,
        edit: EventEditorRoute.EditInItemEditor?,
        groupResults: Flow<GroupSelectionResult>?,
        render: @Composable (EventItemEditorForm) -> Unit,
    ) {
        val store = rememberNavEntryResource(key = EventEditorStore::class) {
            EventEditorStore(
                repository = repository,
                groups = groups,
                eventId = edit?.eventId,
                occurrenceId = edit?.occurrenceId,
                initialDate = initialDate,
                name = name ?: TextFieldState(),
                description = description ?: TextFieldState(),
            )
        }
        val state by store.state.collectAsState()
        LaunchedEffect(key1 = store, key2 = state.isFinished) {
            if (state.isFinished) onFinished()
        }
        LaunchedEffect(key1 = store, key2 = groupResults) {
            groupResults?.collect { result ->
                store.accept(intent = EventEditorIntent.GroupChanged(id = result.groupId))
            }
        }
        val loadError = state.loadError ?: state.groupsLoadError
        render(
            EventItemEditorForm(
                name = state.name,
                description = state.description,
                group = state.groups.find { group -> group.id == state.groupId },
                isLoading = state.isLoading || state.isLoadingGroups,
                loadError = loadError,
                canSave = state.canSave,
                isSaving = state.isSaving,
                enabled = !state.isBusy && !state.isFinished && state.dialog == null && loadError == null,
                onSave = {
                    val intent = if (state.isNew) {
                        EventEditorIntent.SaveNew(groupId = groupId)
                    } else {
                        EventEditorIntent.Save
                    }
                    store.accept(intent = intent)
                },
                onDelete = { store.accept(intent = EventEditorIntent.PrepareDelete) },
                onRetry = {
                    val intent = if (state.loadError != null) {
                        EventEditorIntent.RetryLoad
                    } else {
                        EventEditorIntent.RetryGroups
                    }
                    store.accept(intent = intent)
                },
                onClearGroup = { store.accept(intent = EventEditorIntent.GroupChanged(id = null)) },
                schedule = { EventItemEditorSchedule(state = state, onIntent = store::accept) },
            )
        )
        EventEditorDialogs(
            state = state,
            today = state.today,
            onIntent = store::accept,
            availableHeight = availableHeight,
        )
    }
}

@Composable
private fun EventItemEditorSchedule(state: EventEditorState, onIntent: (EventEditorIntent) -> Unit) {
    val enabled = !state.isBusy && !state.isFinished && state.dialog == null
    Column(verticalArrangement = Arrangement.spacedBy(space = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
            Column(modifier = Modifier.weight(weight = 1f)) {
                EventDateSection(state = state, today = state.today, onIntent = onIntent, end = false)
            }
            Column(modifier = Modifier.weight(weight = 1f)) {
                EventDateSection(state = state, today = state.today, onIntent = onIntent, end = true)
            }
        }
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                Column(modifier = Modifier.weight(weight = 1f)) {
                    EventStartTimeField(state = state, onIntent = onIntent, enabled = enabled, label = "Start time")
                }
                Column(modifier = Modifier.weight(weight = 1f)) {
                    if (state.startTime != null) {
                        EventEndTimeField(state = state, onIntent = onIntent, enabled = enabled)
                    }
                }
            }
            state.timingError?.let { error ->
                Spacer(modifier = Modifier.height(height = 8.dp))
                Text(text = error, style = TaskBTheme.typography.small, color = TaskBTheme.colors.danger)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
            EventRepeatPattern(
                state = state,
                onIntent = onIntent,
                enabled = enabled,
                modifier = Modifier.weight(weight = 1f),
            )
            Box(modifier = Modifier.weight(weight = 1f)) {
                EventRepeatOn(
                    state = state,
                    onIntent = onIntent,
                    enabled = enabled,
                    wide = true,
                    showHeading = true,
                )
            }
        }
        if (state.recurrence != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(space = 24.dp)) {
                EventRepeatEnd(
                    state = state,
                    onIntent = onIntent,
                    enabled = enabled,
                    modifier = Modifier.weight(weight = 1f),
                )
                Spacer(modifier = Modifier.weight(weight = 1f))
            }
        }
    }
}
