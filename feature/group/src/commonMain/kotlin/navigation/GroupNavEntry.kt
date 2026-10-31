package com.mkulikov.taskb.feature.group.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.group.api.GroupEditorRoute
import com.mkulikov.taskb.feature.group.api.GroupsRoute
import com.mkulikov.taskb.feature.group.api.GroupSelectionRoute
import com.mkulikov.taskb.feature.group.screen.group_selection.GroupSelectionScreen
import com.mkulikov.taskb.feature.group.screen.group_editor.GroupEditorScreen
import com.mkulikov.taskb.feature.group.screen.groups.GroupsScreen

internal class GroupNavEntry : INavEntry {
    override fun register(scope: EntryProviderScope<NavKey>, navigator: IAppNavigator) {
        scope.entry<GroupSelectionRoute> { route ->
            GroupSelectionScreen(route, onBack = navigator::back,
                onCreateGroup = { navigator.navigate(GroupEditorRoute.Create) },
                onSelected = { navigator.backWithResult(route.resultKey, it) })
        }
        scope.entry<GroupsRoute> {
            GroupsScreen(
                onBack = navigator::back,
                onCreateGroup = { navigator.navigate(GroupEditorRoute.Create) },
                onEditGroup = { navigator.navigate(GroupEditorRoute.Edit(it)) },
            )
        }
        scope.entry<GroupEditorRoute.Create> { GroupEditorScreen(onBack = navigator::back) }
        scope.entry<GroupEditorRoute.Edit> { route -> GroupEditorScreen(groupId = route.groupId, onBack = navigator::back) }
    }
}
