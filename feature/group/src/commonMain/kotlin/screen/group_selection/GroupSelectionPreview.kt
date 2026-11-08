package com.mkulikov.taskb.feature.group.screen.group_selection

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.feature.group.preview.previewGroups
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Preview(name = "Choose group – wide", widthDp = 1280, heightDp = 720)
@Preview(name = "Choose group – compact", widthDp = 400, heightDp = 720)
@Composable
private fun GroupSelectionPreview() {
    val state = remember {
        GroupSelectionState(
            selectedGroupId = previewGroups.first().id,
            showNoGroup = true,
            groups = previewGroups,
            isLoading = false,
        )
    }
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            GroupSelectionContent(state = state, onIntent = {}, onBack = {}, onCreateGroup = {})
        }
    }
}
