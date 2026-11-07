package com.mkulikov.taskb.feature.event.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.event.api.EventEditorRoute
import com.mkulikov.taskb.feature.event.screen.event_editor.EventEditorScreen
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.GroupSelectionRoute
import kotlinx.coroutines.flow.MutableStateFlow

internal class EventNavEntry : INavEntry {
    private val calendarEventGroupId = MutableStateFlow<Int?>(value = null)

    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<EventEditorRoute.Create> { route ->
            val groups = rememberNavEntryResource(key = "group-result") {
                navigator.createResultReceiver<GroupSelectionResult>()
            }
            EventEditorScreen(
                initialDate = route.initialDate,
                initialGroupId = route.initialGroupId
                    ?: if (route.rememberGroupSelection) calendarEventGroupId.value else null,
                onBack = navigator::back,
                groupResults = groups.results,
                onGroupSelected = { groupId -> if (route.rememberGroupSelection) calendarEventGroupId.value = groupId },
                onChooseGroup = { groupId ->
                    navigator.navigate(
                        key = GroupSelectionRoute(
                            resultKey = groups.key,
                            selectedGroupId = groupId,
                            showNoGroup = false,
                        )
                    )
                },
            )
        }
        scope.entry<EventEditorRoute.Edit> { route ->
            val groups = rememberNavEntryResource(key = "group-result") {
                navigator.createResultReceiver<GroupSelectionResult>()
            }
            EventEditorScreen(
                eventId = route.eventId,
                occurrenceId = route.occurrenceId,
                onBack = navigator::back,
                groupResults = groups.results,
                onChooseGroup = { groupId ->
                    navigator.navigate(
                        key = GroupSelectionRoute(
                            resultKey = groups.key,
                            selectedGroupId = groupId,
                            showNoGroup = false,
                        )
                    )
                },
            )
        }
    }
}
