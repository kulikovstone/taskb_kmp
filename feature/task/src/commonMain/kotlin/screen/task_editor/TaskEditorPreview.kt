package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@Preview(name = "New task – compact", widthDp = 400, heightDp = 720)
@Composable
private fun TaskEditorCompactPreview() {
    val state = remember {
        TaskEditorState(today = LocalDate(year = 2026, month = 11, day = 8), isLoadingGroups = false)
    }
    TaskEditorPreview(state = state, wide = false)
}

@Preview(name = "Edit repeating task – wide", widthDp = 1280, heightDp = 1060)
@Composable
private fun TaskEditorRecurringPreview() {
    val state = remember {
        val today = LocalDate(year = 2026, month = 11, day = 8)
        val dueDate = LocalDate(year = 2026, month = 11, day = 9)
        val group = Group(id = 1, name = "Work", color = GroupColor.Blue)
        val recurrence = TaskRecurrence(
            frequency = RecurrenceFrequency.Weekly,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            occurrenceCount = 10,
        )
        val task = Task(
            id = 1,
            name = "Review the release checklist",
            dueDate = dueDate,
            groupId = group.id,
            createdAt = Instant.parse(input = "2026-11-01T12:00:00Z"),
            recurrence = recurrence,
        )
        TaskEditorState(
            isNew = false,
            original = task,
            name = TextFieldState(initialText = task.name),
            description = TextFieldState(initialText = "Check the release notes and update the screenshots."),
            dueDate = dueDate,
            recurrence = recurrence,
            groupId = group.id,
            groups = listOf(element = group),
            today = today,
            isLoadingGroups = false,
        )
    }
    TaskEditorPreview(state = state, wide = true)
}

@Composable
private fun TaskEditorPreview(state: TaskEditorState, wide: Boolean) {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            TaskEditorContent(state = state, onIntent = {}, wide = wide, onBack = {}, onChooseGroup = {})
        }
    }
}
