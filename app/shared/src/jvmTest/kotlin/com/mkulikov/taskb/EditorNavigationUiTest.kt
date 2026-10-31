package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.AppRoot
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.feature.home.api.HomeRoute
import com.mkulikov.taskb.feature.group.api.GroupEditorRoute
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EditorNavigationUiTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `all editor routes open without lists and return to their caller after saving`() {
        withAppKoin(temporaryFolder.root.toPath().resolve("direct-editors.db")) { app ->
            val navigator = app.koin.get<IAppNavigator>()
            val database = app.koin.get<AppDatabase>()
            runSkikoComposeUiTest(size = Size(1280f, 900f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { AppRoot(HomeRoute) } } }
                waitHome()
                runOnIdle { navigator.navigate(GroupEditorRoute.Create) }
                onNodeWithContentDescription("Group name").performTextInput("External group")
                onNodeWithContentDescription("Save group").performClick()
                waitHome()
                val groupId = runBlocking { database.groupDao().observeGroups().first().single().id }

                runOnIdle { navigator.navigate(TaskEditorRoute.Create(LocalDate(2026, 10, 28), groupId)) }
                waitUntil { onAllNodes(hasContentDescription("Task name")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Choose task group").assert(hasText("External group"))
                onNodeWithContentDescription("Search tasks").assertDoesNotExist()
                onNodeWithContentDescription("Task name").performTextInput("External task")
                onNodeWithContentDescription("Save task").performClick()
                waitHome()
                val task = runBlocking { database.taskDao().observeTasks().first().single() }
                assertEquals(groupId, task.groupId)
                assertEquals(LocalDate(2026, 10, 28).toEpochDays(), task.dueDate)

                runOnIdle { navigator.navigate(GroupEditorRoute.Edit(groupId)) }
                waitUntil { onAllNodes(hasText("Planned")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Search groups").assertDoesNotExist()
                onNodeWithContentDescription("Group name").performTextReplacement("Renamed group")
                onNodeWithContentDescription("Save group").performClick()
                waitHome()

                runOnIdle { navigator.navigate(TaskEditorRoute.Edit(task.id)) }
                waitUntil { onAllNodes(hasContentDescription("Task name")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Task name").assertTextEquals("External task")
                onNodeWithContentDescription("Choose task group").assert(hasText("Renamed group"))
                onNodeWithContentDescription("Task name").performTextReplacement("Renamed task")
                onNodeWithContentDescription("Save task").performClick()
                waitHome()
                assertEquals("Renamed task", runBlocking { database.taskDao().getById(task.id) }?.name)
            }
        }
    }

    @Test
    fun `group selection handles empty groups cancellation and live updates without losing the draft`() {
        withAppKoin(temporaryFolder.root.toPath().resolve("group-selection.db")) { app ->
            val navigator = app.koin.get<IAppNavigator>()
            val database = app.koin.get<AppDatabase>()
            val date = LocalDate(2026, 11, 4)
            runSkikoComposeUiTest(size = Size(1280f, 900f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { AppRoot(HomeRoute) } } }
                waitHome()
                runOnIdle { navigator.navigate(TaskEditorRoute.Create(initialDate = date)) }
                waitUntil { onAllNodes(hasContentDescription("Task name")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Task name").performTextInput("Draft with date")
                onNodeWithContentDescription("Choose task group").performClick()
                waitUntil { onAllNodes(hasText("No groups yet.")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("No group").assertDoesNotExist()
                onNode(isDialog()).assertDoesNotExist()
                onNodeWithContentDescription("Search groups").performTextInput("Missing")
                onNodeWithText("No groups match your search.").assertIsDisplayed()
                onNodeWithContentDescription("Back").performClick()
                onNodeWithContentDescription("Task name").assertTextEquals("Draft with date")
                onNodeWithContentDescription("Clear task group").assertDoesNotExist()
                onNodeWithContentDescription("Choose task group").performClick()
                onNodeWithContentDescription("Search groups").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                val groupId = runBlocking {
                    database.groupDao().insert(GroupEntity(name = "New group", color = Palette.Blue.s400.toArgb())).toInt()
                }
                val newGroupRow = hasText("New group") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
                waitUntil { onAllNodes(newGroupRow).fetchSemanticsNodes().isNotEmpty() }
                onNode(newGroupRow).performClick()
                onNodeWithContentDescription("Task name").assertTextEquals("Draft with date")
                onNodeWithContentDescription("Choose task group").assert(hasText("New group"))
                onNodeWithContentDescription("Choose task group").performClick()
                onNode(newGroupRow).assertIsSelected()
                onNodeWithContentDescription("Search groups").performTextInput("Missing")
                onNodeWithContentDescription("Back").performClick()
                onNodeWithContentDescription("Choose task group").assert(hasText("New group"))
                onNodeWithContentDescription("Save task").performClick()
                waitHome()
                val task = runBlocking { database.taskDao().observeTasks().first().single() }
                assertEquals(groupId, task.groupId)
                assertEquals(date.toEpochDays(), task.dueDate)
                assertEquals("Draft with date", task.name)
            }
        }
    }

    @Test
    fun `stacked editors keep independent drafts and popping a create screen starts a fresh draft`() {
        withAppKoin(temporaryFolder.root.toPath().resolve("editor-stack.db")) { app ->
            val navigator = app.koin.get<IAppNavigator>()
            val database = app.koin.get<AppDatabase>()
            val groupId = runBlocking { database.groupDao().insert(GroupEntity(name = "Work", color = Palette.Blue.s400.toArgb())).toInt() }
            runSkikoComposeUiTest(size = Size(1280f, 900f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { AppRoot(HomeRoute) } } }
                waitHome()
                runOnIdle { navigator.navigate(TaskEditorRoute.Create(initialGroupId = groupId)) }
                waitUntil { onAllNodes(hasContentDescription("Task name")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Task name").performTextInput("Retained task draft")
                runOnIdle { navigator.navigate(GroupEditorRoute.Edit(groupId)) }
                waitUntil { onAllNodes(hasText("Planned")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Group name").performTextReplacement("Retained group draft")
                runOnIdle { navigator.navigate(GroupEditorRoute.Create) }
                onNodeWithContentDescription("Group name").performTextInput("Temporary draft")
                onNodeWithContentDescription("Back").performClick()
                waitUntil { onAllNodes(hasText("Planned")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Group name").assertTextEquals("Retained group draft")
                onNodeWithContentDescription("Back").performClick()
                waitUntil { onAllNodes(hasContentDescription("Task name")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Task name").assertTextEquals("Retained task draft")
                onNodeWithContentDescription("Choose task group").performClick()
                onNodeWithContentDescription("Search groups").performTextInput("wor")
                onNodeWithText("No group").assertDoesNotExist()
                onNode(isDialog()).assertDoesNotExist()
                onNodeWithText("Work").performClick()
                onNodeWithContentDescription("Task name").assertTextEquals("Retained task draft")
                onNodeWithContentDescription("Back").performClick()
                waitHome()
                runOnIdle { navigator.navigate(GroupEditorRoute.Create) }
                onNodeWithText("Enter group name").assertIsDisplayed()
                onNodeWithContentDescription("Save group").assertIsNotEnabled()
                onNodeWithContentDescription("Back").performClick()
                waitHome()
                runOnIdle { navigator.navigate(TaskEditorRoute.Edit(Int.MAX_VALUE)) }
                waitUntil { onAllNodes(hasText("This task no longer exists.")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Save task").assertIsNotEnabled()
                onNodeWithContentDescription("Back").performClick()
                waitHome()
                runOnIdle { navigator.navigate(GroupEditorRoute.Edit(Int.MAX_VALUE)) }
                waitUntil { onAllNodes(hasText("This group no longer exists.")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Save group").assertIsNotEnabled()
            }
        }
    }

    private suspend fun SkikoComposeUiTest.waitHome() {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Tasks") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Groups") and hasClickAction()).assertIsDisplayed()
    }
}
