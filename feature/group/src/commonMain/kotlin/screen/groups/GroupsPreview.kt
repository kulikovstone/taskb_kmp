package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.feature.group.preview.previewGroups
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Preview(name = "Groups – wide", widthDp = 1280, heightDp = 720)
@Composable
private fun GroupsWidePreview() = GroupsPreview(wide = true)

@Preview(name = "Groups – compact", widthDp = 400, heightDp = 720)
@Composable
private fun GroupsCompactPreview() = GroupsPreview(wide = false)

@Composable
private fun GroupsPreview(wide: Boolean) {
    val state = remember(key1 = wide) {
        GroupsState(
            groups = previewGroups,
            isLoading = false,
            filter = if (wide) GroupFilter.All else GroupFilter.WithTasks,
            sort = if (wide) GroupSort.Tasks else GroupSort.Name,
        )
    }
    TaskBTheme(darkTheme = !wide) {
        Surface(color = TaskBTheme.colors.surface) {
            if (wide) {
                GroupsWideScreen(state = state, onIntent = {}, onBack = {}, onCreateGroup = {}, onEditGroup = {})
            } else {
                GroupsCompactScreen(state = state, onIntent = {}, onBack = {}, onCreateGroup = {}, onEditGroup = {})
            }
        }
    }
}
