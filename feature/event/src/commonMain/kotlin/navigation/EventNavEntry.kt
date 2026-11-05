package com.mkulikov.taskb.feature.event.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.GroupSelectionRoute
import com.mkulikov.taskb.feature.event.api.EventEditorRoute
import com.mkulikov.taskb.feature.event.screen.event_editor.EventEditorScreen
import kotlinx.coroutines.flow.MutableStateFlow

internal class EventNavEntry : INavEntry {
    private val calendarEventGroupId = MutableStateFlow<Int?>(null)

    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<EventEditorRoute.Create> { route ->
            val groups = rememberNavEntryResource("group-result") { navigator.createResultReceiver<GroupSelectionResult>() }
            EventEditorScreen(initialDate = route.initialDate,
                initialGroupId = route.initialGroupId ?: if (route.rememberGroupSelection) calendarEventGroupId.value else null,
                onBack = navigator::back,
                groupResults = groups.results,
                onGroupSelected = { if (route.rememberGroupSelection) calendarEventGroupId.value = it },
                onChooseGroup = { navigator.navigate(GroupSelectionRoute(groups.key, it, showNoGroup = false)) })
        }
        scope.entry<EventEditorRoute.Edit> { route ->
            val groups = rememberNavEntryResource("group-result") { navigator.createResultReceiver<GroupSelectionResult>() }
            EventEditorScreen(eventId = route.eventId, occurrenceId = route.occurrenceId, onBack = navigator::back,
                groupResults = groups.results,
                onChooseGroup = { navigator.navigate(GroupSelectionRoute(groups.key, it, showNoGroup = false)) })
        }
    }
}
