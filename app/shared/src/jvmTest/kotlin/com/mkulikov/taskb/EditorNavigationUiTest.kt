package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.AppRoot
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.feature.group.api.GroupEditorRoute
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.home.api.HomeRoute
import com.mkulikov.taskb.feature.task.api.TaskCreationRoute
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class EditorNavigationUiTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `all editor routes open without lists and return to their caller after saving`() {
        withAppKoin(file = temporaryFolder.root.toPath().resolve("direct-editors.db")) { app ->
            val navigator = app.koin.get<IAppNavigator>()
            val database = app.koin.get<AppDatabase>()
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 900f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { AppRoot(startDestination = HomeRoute) }
                    }
                }
                waitHome()
                runOnIdle { navigator.navigate(key = GroupEditorRoute.Create) }
                onNodeWithContentDescription(label = "Group name").performTextInput(text = "External group")
                onNodeWithContentDescription(label = "Save group").performClick()
                waitHome()
                val groupId = runBlocking { database.groupDao().observeGroups().first().single().id }

                runOnIdle {
                    navigator.navigate(
                        key = TaskCreationRoute(
                            initialDate = LocalDate(year = 2026, month = 10, day = 28),
                            initialGroupId = groupId,
                        )
                    )
                }
                waitUntil {
                    onAllNodes(matcher = hasContentDescription(value = "Task name"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Choose group")
                    .assert(matcher = hasText(text = "External group"))
                onNodeWithContentDescription(label = "Search tasks").assertDoesNotExist()
                onNodeWithContentDescription(label = "Task name").performTextInput(text = "External task")
                onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                waitHome()
                val task = runBlocking { database.taskDao().observeTasks().first().single() }
                assertEquals(expected = groupId, actual = task.groupId)
                assertEquals(
                    expected = LocalDate(year = 2026, month = 10, day = 28).toEpochDays(),
                    actual = task.dueDate,
                )

                runOnIdle { navigator.navigate(key = GroupEditorRoute.Edit(groupId = groupId)) }
                waitUntil { onAllNodes(matcher = hasText(text = "Planned")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription(label = "Search groups").assertDoesNotExist()
                onNodeWithContentDescription(label = "Group name").performTextReplacement(text = "Renamed group")
                onNodeWithContentDescription(label = "Save group").performClick()
                waitHome()

                runOnIdle { navigator.navigate(key = TaskEditorRoute(taskId = task.id)) }
                waitUntil {
                    onAllNodes(matcher = hasContentDescription(value = "Task name"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Task name").assertTextEquals("External task")
                onNodeWithContentDescription(label = "Choose group")
                    .assert(matcher = hasText(text = "Renamed group"))
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Renamed task")
                onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                waitHome()
                assertEquals(
                    expected = "Renamed task",
                    actual = runBlocking { database.taskDao().getById(id = task.id) }?.name,
                )
            }
        }
    }

    @Test
    fun `group selection handles empty groups cancellation and live updates without losing the draft`() {
        withAppKoin(file = temporaryFolder.root.toPath().resolve("group-selection.db")) { app ->
            val navigator = app.koin.get<IAppNavigator>()
            val database = app.koin.get<AppDatabase>()
            val date = LocalDate(year = 2026, month = 11, day = 4)
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 900f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { AppRoot(startDestination = HomeRoute) }
                    }
                }
                waitHome()
                runOnIdle { navigator.navigate(key = TaskCreationRoute(initialDate = date)) }
                waitUntil {
                    onAllNodes(matcher = hasContentDescription(value = "Task name"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Task name").performTextInput(text = "Draft with date")
                onNodeWithContentDescription(label = "Choose group").performClick()
                waitUntil {
                    onAllNodes(matcher = hasText(text = "No groups yet.")).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText(text = "No group").assertDoesNotExist()
                onNode(matcher = isDialog()).assertDoesNotExist()
                onNodeWithContentDescription(label = "Search groups").performTextInput(text = "Missing")
                onNodeWithText(text = "No groups match your search.").assertIsDisplayed()
                onNodeWithContentDescription(label = "Back").performClick()
                onNodeWithContentDescription(label = "Task name").assertTextEquals("Draft with date")
                onNodeWithContentDescription(label = "Clear group").assertDoesNotExist()
                onNodeWithContentDescription(label = "Choose group").performClick()
                onNodeWithContentDescription(label = "Search groups")
                    .assert(
                        matcher = SemanticsMatcher.expectValue(
                            key = SemanticsProperties.EditableText,
                            expectedValue = AnnotatedString(text = ""),
                        )
                    )
                val groupId = runBlocking {
                    database
                        .groupDao()
                        .insert(group = GroupEntity(name = "New group", color = Palette.Blue.s400.toArgb()))
                        .toInt()
                }
                val newGroupRow = hasText(text = "New group") and
                    SemanticsMatcher.keyIsDefined(key = SemanticsProperties.Selected)
                waitUntil { onAllNodes(matcher = newGroupRow).fetchSemanticsNodes().isNotEmpty() }
                onNode(matcher = newGroupRow).performClick()
                onNodeWithContentDescription(label = "Task name").assertTextEquals("Draft with date")
                onNodeWithContentDescription(label = "Choose group")
                    .assert(matcher = hasText(text = "New group"))
                onNodeWithContentDescription(label = "Choose group").performClick()
                onNode(matcher = newGroupRow).assertIsSelected()
                onNodeWithContentDescription(label = "Search groups").performTextInput(text = "Missing")
                onNodeWithContentDescription(label = "Back").performClick()
                onNodeWithContentDescription(label = "Choose group")
                    .assert(matcher = hasText(text = "New group"))
                onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                waitHome()
                val task = runBlocking { database.taskDao().observeTasks().first().single() }
                assertEquals(expected = groupId, actual = task.groupId)
                assertEquals(expected = date.toEpochDays(), actual = task.dueDate)
                assertEquals(expected = "Draft with date", actual = task.name)
            }
        }
    }

    @Test
    fun `stacked editors keep independent drafts and popping a create screen starts a fresh draft`() {
        withAppKoin(file = temporaryFolder.root.toPath().resolve("editor-stack.db")) { app ->
            val navigator = app.koin.get<IAppNavigator>()
            val database = app.koin.get<AppDatabase>()
            val groupId = runBlocking {
                database
                    .groupDao()
                    .insert(group = GroupEntity(name = "Work", color = Palette.Blue.s400.toArgb()))
                    .toInt()
            }
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 900f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { AppRoot(startDestination = HomeRoute) }
                    }
                }
                waitHome()
                runOnIdle { navigator.navigate(key = TaskCreationRoute(initialGroupId = groupId)) }
                waitUntil {
                    onAllNodes(matcher = hasContentDescription(value = "Task name"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Task name").performTextInput(text = "Retained task draft")
                runOnIdle { navigator.navigate(key = GroupEditorRoute.Edit(groupId = groupId)) }
                waitUntil { onAllNodes(matcher = hasText(text = "Planned")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription(label = "Group name")
                    .performTextReplacement(text = "Retained group draft")
                runOnIdle { navigator.navigate(key = GroupEditorRoute.Create) }
                onNodeWithContentDescription(label = "Group name").performTextInput(text = "Temporary draft")
                onNodeWithContentDescription(label = "Back").performClick()
                waitUntil { onAllNodes(matcher = hasText(text = "Planned")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription(label = "Group name").assertTextEquals("Retained group draft")
                onNodeWithContentDescription(label = "Back").performClick()
                waitUntil {
                    onAllNodes(matcher = hasContentDescription(value = "Task name"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Task name").assertTextEquals("Retained task draft")
                onNodeWithContentDescription(label = "Choose group").performClick()
                onNodeWithContentDescription(label = "Search groups").performTextInput(text = "wor")
                onNodeWithText(text = "No group").assertDoesNotExist()
                onNode(matcher = isDialog()).assertDoesNotExist()
                onNodeWithText(text = "Work").performClick()
                onNodeWithContentDescription(label = "Task name").assertTextEquals("Retained task draft")
                onNodeWithContentDescription(label = "Back").performClick()
                waitHome()
                runOnIdle { navigator.navigate(key = GroupEditorRoute.Create) }
                onNodeWithText(text = "Enter group name").assertIsDisplayed()
                onNodeWithContentDescription(label = "Save group").assertIsNotEnabled()
                onNodeWithContentDescription(label = "Back").performClick()
                waitHome()
                runOnIdle { navigator.navigate(key = TaskEditorRoute(taskId = Int.MAX_VALUE)) }
                waitUntil {
                    onAllNodes(matcher = hasText(text = "This task no longer exists."))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).assertIsNotEnabled()
                onNodeWithContentDescription(label = "Back").performClick()
                waitHome()
                runOnIdle { navigator.navigate(key = GroupEditorRoute.Edit(groupId = Int.MAX_VALUE)) }
                waitUntil {
                    onAllNodes(matcher = hasText(text = "This group no longer exists."))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Save group").assertIsNotEnabled()
            }
        }
    }

    private suspend fun SkikoComposeUiTest.waitHome() {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = "Tasks") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        onNode(matcher = hasText(text = "Groups") and hasClickAction()).assertIsDisplayed()
    }
}
