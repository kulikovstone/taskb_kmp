package com.mkulikov.taskb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.AppRoot
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.home.api.HomeRoute
import com.mkulikov.taskb.feature.task.api.TaskEditorRoute
import com.mkulikov.taskb.feature.task.api.TaskRoute
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
class GroupSelectionNavigationUiTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `create and select a group from either toolbar while preserving search and drafts across resizing`() {
        for (initialWidth in listOf(400, 1280)) {
            withAppKoin(temporaryFolder.root.toPath().resolve("create-group-$initialWidth.db")) { app ->
                val navigator = app.koin.get<IAppNavigator>()
                val database = app.koin.get<AppDatabase>()
                val width = mutableStateOf(initialWidth.dp)
                val date = LocalDate(2026, 11, 4)
                runSkikoComposeUiTest(size = Size(1280f, 980f)) {
                    setContent {
                        KoinIsolatedContext(app) {
                            TaskBTheme(false) {
                                Box(Modifier.width(width.value).fillMaxHeight()) { AppRoot(HomeRoute) }
                            }
                        }
                    }
                    waitForNode(hasText("Tasks") and hasClickAction())
                    runOnIdle { navigator.navigate(TaskEditorRoute.Create(initialDate = date)) }
                    waitForNode(hasContentDescription("Task name"))
                    onNodeWithContentDescription("Task name").performTextInput("Retained task draft")
                    onNodeWithContentDescription("Choose task group").performClick()
                    waitForNode(hasText("No groups yet."))
                    onNodeWithText("No group").assertDoesNotExist()
                    onNodeWithContentDescription("Search groups").performTextInput("Travel")
                    onNodeWithText("No groups match your search.").assertIsDisplayed()
                    assertCreateAction(initialWidth >= 840)
                    if (initialWidth >= 840) onNodeWithText("New group").performClick()
                    else onNodeWithContentDescription("Add group").performClick()

                    waitForNode(hasContentDescription("Group name"))
                    onNodeWithContentDescription("Group name").performTextInput("Travel")
                    onNodeWithContentDescription("Orange").performClick()
                    val nextWidth = if (initialWidth >= 840) 400 else 1280
                    runOnIdle { width.value = nextWidth.dp }
                    waitForIdle()
                    onNodeWithContentDescription("Group name").assertTextEquals("Travel")
                    onNodeWithContentDescription("Orange").assertIsSelected()
                    onNodeWithContentDescription("Save group").performClick()

                    waitForNode(hasContentDescription("Search groups"))
                    onNodeWithContentDescription("Search groups").assertTextEquals("Travel")
                    assertCreateAction(nextWidth >= 840)
                    waitForNode(groupRow("Travel"))
                    onNode(groupRow("Travel")).assertIsDisplayed().performClick()
                    waitForNode(hasContentDescription("Task name"))
                    onNodeWithContentDescription("Task name").assertTextEquals("Retained task draft")
                    onNodeWithContentDescription("Choose task group").assert(hasText("Travel"))
                    onNodeWithContentDescription("Save task").performClick()
                    waitForNode(hasText("Tasks") and hasClickAction())

                    val group = runBlocking { database.groupDao().observeGroups().first().single() }
                    val task = runBlocking { database.taskDao().observeTasks().first().single() }
                    assertEquals("Travel", group.name)
                    assertEquals(Palette.Orange.s400.toArgb(), group.color)
                    assertEquals(group.id, task.groupId)
                    assertEquals("Retained task draft", task.name)
                    assertEquals(date.toEpochDays(), task.dueDate)
                }
            }
        }
    }

    @Test
    fun `cancelling creation preserves selector query selection and the calling task filter`() {
        withAppKoin(temporaryFolder.root.toPath().resolve("cancel-group.db")) { app ->
            val navigator = app.koin.get<IAppNavigator>()
            val database = app.koin.get<AppDatabase>()
            runBlocking { database.groupDao().insert(GroupEntity(name = "Work", color = Palette.Blue.s400.toArgb())) }
            runSkikoComposeUiTest(size = Size(1280f, 900f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { AppRoot(HomeRoute) } } }
                waitForNode(hasText("Tasks") and hasClickAction())
                runOnIdle { navigator.navigate(TaskRoute) }
                waitForNode(hasContentDescription("Search tasks"))
                onNodeWithContentDescription("Choose group").performClick()
                waitForNode(groupRow("Work"))
                onNode(groupRow("Work")).performClick()
                waitForNode(hasContentDescription("Search tasks"))
                onNodeWithContentDescription("Choose group").performClick()
                waitForNode(hasContentDescription("Search groups"))
                onNode(groupRow("Work")).assertIsSelected()
                onNodeWithContentDescription("Search groups").performTextInput("Work")
                onNodeWithText("New group").performClick()
                waitForNode(hasContentDescription("Group name"))
                onNodeWithContentDescription("Group name").performTextInput("Discarded group")
                onNodeWithContentDescription("Back").performClick()

                waitForNode(hasContentDescription("Search groups"))
                onNodeWithContentDescription("Search groups").assertTextEquals("Work")
                onNode(groupRow("Work")).assertIsSelected()
                onNodeWithContentDescription("Back").performClick()
                waitForNode(hasContentDescription("Search tasks"))
                onNodeWithText("Work").assertIsDisplayed()
                assertEquals(listOf("Work"), runBlocking { database.groupDao().observeGroups().first().map { it.name } })

                onNodeWithContentDescription("Choose group").performClick()
                waitForNode(groupRow("No group"))
                onNode(groupRow("No group")).performClick()
                waitForNode(hasContentDescription("Search tasks"))
                onNodeWithText("No group").assertIsDisplayed()
                onNodeWithContentDescription("Choose group").performClick()
                waitForNode(hasContentDescription("Search groups"))
                onNode(groupRow("No group")).assertIsSelected()
            }
        }
    }

    private fun SkikoComposeUiTest.assertCreateAction(wide: Boolean) {
        if (wide) {
            onNodeWithText("New group").assertIsDisplayed()
            onNodeWithContentDescription("Add group").assertDoesNotExist()
        } else {
            onNodeWithContentDescription("Add group").assertIsDisplayed()
            onNodeWithText("New group").assertDoesNotExist()
        }
    }

    private fun groupRow(name: String): SemanticsMatcher =
        hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    private suspend fun SkikoComposeUiTest.waitForNode(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }
}
