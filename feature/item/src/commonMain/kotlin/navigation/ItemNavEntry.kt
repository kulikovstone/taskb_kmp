package com.mkulikov.taskb.feature.item.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.GroupSelectionRoute
import com.mkulikov.taskb.feature.item.api.ItemEditorRoute
import com.mkulikov.taskb.feature.item.screen.item_editor.ItemEditorScreen

internal class ItemNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<ItemEditorRoute> { route ->
            val groups = rememberNavEntryResource(key = "group-result") {
                navigator.createResultReceiver<GroupSelectionResult>()
            }
            ItemEditorScreen(
                route = route,
                onBack = navigator::back,
                groupResults = groups.results,
                onChooseGroup = { groupId ->
                    navigator.navigate(
                        key = GroupSelectionRoute(
                            resultKey = groups.key,
                            selectedGroupId = groupId,
                            showNoGroup = false,
                        ),
                    )
                },
            )
        }
    }
}
