package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskFilter
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@Preview(name = "Tasks – wide", widthDp = 1280, heightDp = 720)
@Composable
private fun TaskListWidePreview() = TaskListPreview(wide = true)

@Preview(name = "Tasks with filters – compact", widthDp = 400, heightDp = 720)
@Composable
private fun TaskListCompactPreview() = TaskListPreview(wide = false)

@Composable
private fun TaskListPreview(wide: Boolean) {
    val state = remember(key1 = wide) {
        val today = LocalDate(year = 2026, month = 11, day = 8)
        val now = Instant.parse(input = "2026-11-08T12:00:00Z")
        val groups = listOf(
            Group(id = 1, name = "Work", color = GroupColor.Blue),
            Group(id = 2, name = "Personal", color = GroupColor.Green),
        )
        TaskState(
            tasks = listOf(
                Task(
                    id = 1,
                    name = "Review release notes",
                    description = "Check the changelog and screenshots before publishing.",
                    dueDate = LocalDate(year = 2026, month = 11, day = 7),
                    groupId = 1,
                    createdAt = now,
                ),
                Task(id = 2, name = "Share the design", dueDate = today, groupId = 1, createdAt = now),
                Task(
                    id = 3,
                    name = "Read a chapter",
                    dueDate = today,
                    groupId = 2,
                    recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily),
                    createdAt = now,
                ),
                Task(id = 4, name = "Organize project notes", createdAt = now),
                Task(id = 5, name = "Send the brief", groupId = 1, createdAt = now, completedAt = now),
            ),
            groups = groups,
            today = today,
            now = now,
            groupFilter = if (wide) TaskGroupFilter.All else TaskGroupFilter.Selected(id = 1),
            filter = if (wide) null else TaskFilter.Today,
            isLoading = false,
        )
    }
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            TaskListScreen(
                state = state,
                onIntent = {},
                wide = wide,
                onBack = {},
                onChooseGroup = {},
                onNewTask = {},
                onOpenTask = {},
            )
        }
    }
}

@Preview(name = "Task filters – compact", widthDp = 400, heightDp = 640)
@Composable
private fun TaskSelectionScreenPreview() {
    val state = remember { taskSelectionPreviewState() }
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            TaskSelectionScreen(state = state, onIntent = {})
        }
    }
}

@Preview(name = "Task filters – dialog", widthDp = 560, heightDp = 560)
@Composable
private fun TaskSelectionDialogPreview() {
    val state = remember { taskSelectionPreviewState() }
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.subtle) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TaskSelectionDialog(state = state, onIntent = {}, availableHeight = 560.dp)
            }
        }
    }
}

private fun taskSelectionPreviewState() = TaskState(
    today = LocalDate(year = 2026, month = 11, day = 8),
    now = Instant.parse(input = "2026-11-08T12:00:00Z"),
    filter = TaskFilter.Today,
    isLoading = false,
)
