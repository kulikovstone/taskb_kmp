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
import com.mkulikov.taskb.feature.event.api.EventCreationForm
import com.mkulikov.taskb.feature.event.api.IEventCreationEditor
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
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate

internal class EventCreationEditor(
    private val repository: IEventRepository,
    private val groups: IGroupRepository,
) : IEventCreationEditor {
    @Composable
    override fun Content(
        name: TextFieldState,
        description: TextFieldState,
        initialDate: LocalDate?,
        groupId: Int?,
        availableHeight: Dp,
        onSaved: () -> Unit,
        render: @Composable (EventCreationForm) -> Unit,
    ) {
        val store = rememberNavEntryResource(key = EventEditorStore::class) {
            EventEditorStore(
                repository = repository,
                groups = groups,
                initialDate = initialDate,
                name = name,
                description = description,
            )
        }
        val state by store.state.collectAsState()
        LaunchedEffect(key1 = store, key2 = state.isFinished) {
            if (state.isFinished) onSaved()
        }
        render(
            EventCreationForm(
                canSave = state.canSave,
                isSaving = state.isSaving,
                enabled = !state.isBusy && !state.isFinished && state.dialog == null,
                onSave = { store.accept(intent = EventEditorIntent.SaveNew(groupId = groupId)) },
                schedule = { EventCreationSchedule(state = state, onIntent = store::accept) },
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
private fun EventCreationSchedule(state: EventEditorState, onIntent: (EventEditorIntent) -> Unit) {
    val enabled = !state.isBusy && !state.isFinished && state.dialog == null
    val error = state.groupsLoadError
    when {
        state.isLoadingGroups -> TaskBLoading()
        error != null -> TaskBLoadError(message = error, onRetry = { onIntent(EventEditorIntent.RetryGroups) })
        else -> Column(verticalArrangement = Arrangement.spacedBy(space = 24.dp)) {
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
}
