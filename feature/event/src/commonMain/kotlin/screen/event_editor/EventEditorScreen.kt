package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.ui.component.TaskBEditorTopAppBar
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject

@Composable
internal fun EventEditorScreen(
    eventId: Int? = null, initialDate: LocalDate? = null, initialGroupId: Int? = null,
    onBack: () -> Unit, groupResults: Flow<GroupSelectionResult>, onChooseGroup: (Int?) -> Unit,
    occurrenceId: EventOccurrenceId? = null,
    onGroupSelected: (Int?) -> Unit = {},
) {
    val repository = koinInject<IEventRepository>()
    val groups = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource("event-editor") {
        EventEditorStore(repository, groups, eventId, initialDate, initialGroupId,
            occurrenceId = occurrenceId, onGroupSelected = onGroupSelected)
    }
    val state by store.state.collectAsState()
    LaunchedEffect(store, state.isFinished) { if (state.isFinished) onBack() }
    LaunchedEffect(store, groupResults) {
        groupResults.collect { store.accept(EventEditorIntent.GroupChanged(it.groupId)) }
    }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            EventEditorContent(state, store::accept, wide, onBack, onChooseGroup = { onChooseGroup(state.groupId) })
            EventEditorDialogs(state, state.today, store::accept, maxHeight)
        }
    }
}

@Composable
internal fun EventEditorContent(state: EventEditorState, onIntent: (EventEditorIntent) -> Unit, wide: Boolean, onBack: () -> Unit, onChooseGroup: () -> Unit) {
    val title = if (state.isNew) "New event" else "Edit event"
    val canDelete = !state.isBusy && state.dialog == null && !state.isFinished && state.original != null
    Column(Modifier.fillMaxSize()) {
        if (wide) TaskBEditorTopAppBar(title, onBack, !state.isSaving) {
            if (!state.isNew) TaskBEditorButton("Delete", TaskBIcons.Delete, { onIntent(EventEditorIntent.PrepareDelete) },
                enabled = canDelete, destructive = true, modifier = Modifier.semantics { contentDescription = "Delete event" })
            TaskBEditorButton(if (state.isNew) "Create event" else "Save changes", if (state.isNew) TaskBIcons.Add else TaskBIcons.Done,
                { onIntent(EventEditorIntent.Save) }, enabled = state.canSave, modifier = Modifier.semantics { contentDescription = "Save event" })
        } else TaskBTopAppBar(title, onBack, backEnabled = !state.isSaving, titleSpacing = 4.dp) {
            if (!state.isNew) IconButton({ onIntent(EventEditorIntent.PrepareDelete) }, enabled = canDelete) {
                Icon(TaskBIcons.Delete, "Delete event", tint = TaskBTheme.colors.danger)
            }
            IconButton({ onIntent(EventEditorIntent.Save) }, enabled = state.canSave) {
                Icon(TaskBIcons.Done, "Save event", tint = if (state.canSave) TaskBTheme.colors.accent else TaskBTheme.colors.border)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (wide) 32.dp else 0.dp), contentAlignment = Alignment.TopCenter) {
            val modifier = Modifier.widthIn(max = if (wide) 720.dp else androidx.compose.ui.unit.Dp.Infinity).fillMaxSize()
            when {
                state.isLoading || state.isLoadingGroups -> TaskBLoading(modifier)
                state.loadError != null -> TaskBLoadError(state.loadError, { onIntent(EventEditorIntent.RetryLoad) }, modifier)
                state.groupsLoadError != null -> TaskBLoadError(state.groupsLoadError, { onIntent(EventEditorIntent.RetryGroups) }, modifier)
                else -> EventEditorForm(state, state.groups, state.today, onIntent,
                    onChooseGroup, wide, modifier)
            }
        }
    }
}
