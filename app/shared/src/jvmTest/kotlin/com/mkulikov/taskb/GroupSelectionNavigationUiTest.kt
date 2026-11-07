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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class GroupSelectionNavigationUiTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `create and select a group from either toolbar while preserving search and drafts across resizing`() {
        for (initialWidth in listOf(400, 1280)) {
            withAppKoin(file = temporaryFolder.root.toPath().resolve("create-group-$initialWidth.db")) { app ->
                val navigator = app.koin.get<IAppNavigator>()
                val database = app.koin.get<AppDatabase>()
                val width = mutableStateOf(value = initialWidth.dp)
                val date = LocalDate(year = 2026, month = 11, day = 4)
                runSkikoComposeUiTest(size = Size(width = 1280f, height = 980f)) {
                    setContent {
                        KoinIsolatedContext(context = app) {
                            TaskBTheme(darkTheme = false) {
                                Box(modifier = Modifier.width(width = width.value).fillMaxHeight()) {
                                    AppRoot(startDestination = HomeRoute)
                                }
                            }
                        }
                    }
                    waitForNode(matcher = hasText(text = "Tasks") and hasClickAction())
                    runOnIdle { navigator.navigate(key = TaskEditorRoute.Create(initialDate = date)) }
                    waitForNode(matcher = hasContentDescription(value = "Task name"))
                    onNodeWithContentDescription(label = "Task name").performTextInput(text = "Retained task draft")
                    onNodeWithContentDescription(label = "Choose task group").performClick()
                    waitForNode(matcher = hasText(text = "No groups yet."))
                    onNodeWithText(text = "No group").assertDoesNotExist()
                    onNodeWithContentDescription(label = "Search groups").performTextInput(text = "Travel")
                    onNodeWithText(text = "No groups match your search.").assertIsDisplayed()
                    assertCreateAction(wide = initialWidth >= 840)
                    if (initialWidth >= 840) {
                        onNodeWithText(text = "New group").performClick()
                    } else {
                        onNodeWithContentDescription(label = "Add group").performClick()
                    }

                    waitForNode(matcher = hasContentDescription(value = "Group name"))
                    onNodeWithContentDescription(label = "Group name").performTextInput(text = "Travel")
                    onNodeWithContentDescription(label = "Orange").performClick()
                    val nextWidth = if (initialWidth >= 840) 400 else 1280
                    runOnIdle { width.value = nextWidth.dp }
                    waitForIdle()
                    onNodeWithContentDescription(label = "Group name").assertTextEquals("Travel")
                    onNodeWithContentDescription(label = "Orange").assertIsSelected()
                    onNodeWithContentDescription(label = "Save group").performClick()

                    waitForNode(matcher = hasContentDescription(value = "Search groups"))
                    onNodeWithContentDescription(label = "Search groups").assertTextEquals("Travel")
                    assertCreateAction(wide = nextWidth >= 840)
                    waitForNode(matcher = groupRow(name = "Travel"))
                    onNode(matcher = groupRow(name = "Travel")).assertIsDisplayed().performClick()
                    waitForNode(matcher = hasContentDescription(value = "Task name"))
                    onNodeWithContentDescription(label = "Task name").assertTextEquals("Retained task draft")
                    onNodeWithContentDescription(label = "Choose task group")
                        .assert(matcher = hasText(text = "Travel"))
                    onNodeWithContentDescription(label = "Save task").performClick()
                    waitForNode(matcher = hasText(text = "Tasks") and hasClickAction())

                    val group = runBlocking { database.groupDao().observeGroups().first().single() }
                    val task = runBlocking { database.taskDao().observeTasks().first().single() }
                    assertEquals(expected = "Travel", actual = group.name)
                    assertEquals(expected = Palette.Orange.s400.toArgb(), actual = group.color)
                    assertEquals(expected = group.id, actual = task.groupId)
                    assertEquals(expected = "Retained task draft", actual = task.name)
                    assertEquals(expected = date.toEpochDays(), actual = task.dueDate)
                }
            }
        }
    }

    @Test
    fun `cancelling creation preserves selector query selection and the calling task filter`() {
        withAppKoin(file = temporaryFolder.root.toPath().resolve("cancel-group.db")) { app ->
            val navigator = app.koin.get<IAppNavigator>()
            val database = app.koin.get<AppDatabase>()
            runBlocking {
                database.groupDao().insert(group = GroupEntity(name = "Work", color = Palette.Blue.s400.toArgb()))
            }
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 900f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { AppRoot(startDestination = HomeRoute) }
                    }
                }
                waitForNode(matcher = hasText(text = "Tasks") and hasClickAction())
                runOnIdle { navigator.navigate(key = TaskRoute) }
                waitForNode(matcher = hasContentDescription(value = "Search tasks"))
                onNodeWithContentDescription(label = "Choose group").performClick()
                waitForNode(matcher = groupRow(name = "Work"))
                onNode(matcher = groupRow(name = "Work")).performClick()
                waitForNode(matcher = hasContentDescription(value = "Search tasks"))
                onNodeWithContentDescription(label = "Choose group").performClick()
                waitForNode(matcher = hasContentDescription(value = "Search groups"))
                onNode(matcher = groupRow(name = "Work")).assertIsSelected()
                onNodeWithContentDescription(label = "Search groups").performTextInput(text = "Work")
                onNodeWithText(text = "New group").performClick()
                waitForNode(matcher = hasContentDescription(value = "Group name"))
                onNodeWithContentDescription(label = "Group name").performTextInput(text = "Discarded group")
                onNodeWithContentDescription(label = "Back").performClick()

                waitForNode(matcher = hasContentDescription(value = "Search groups"))
                onNodeWithContentDescription(label = "Search groups").assertTextEquals("Work")
                onNode(matcher = groupRow(name = "Work")).assertIsSelected()
                onNodeWithContentDescription(label = "Back").performClick()
                waitForNode(matcher = hasContentDescription(value = "Search tasks"))
                onNodeWithText(text = "Work").assertIsDisplayed()
                assertEquals(
                    expected = listOf(element = "Work"),
                    actual = runBlocking { database.groupDao().observeGroups().first().map { group -> group.name } },
                )

                onNodeWithContentDescription(label = "Choose group").performClick()
                waitForNode(matcher = groupRow(name = "No group"))
                onNode(matcher = groupRow(name = "No group")).performClick()
                waitForNode(matcher = hasContentDescription(value = "Search tasks"))
                onNodeWithText(text = "No group").assertIsDisplayed()
                onNodeWithContentDescription(label = "Choose group").performClick()
                waitForNode(matcher = hasContentDescription(value = "Search groups"))
                onNode(matcher = groupRow(name = "No group")).assertIsSelected()
            }
        }
    }

    private fun SkikoComposeUiTest.assertCreateAction(wide: Boolean) {
        if (wide) {
            onNodeWithText(text = "New group").assertIsDisplayed()
            onNodeWithContentDescription(label = "Add group").assertDoesNotExist()
        } else {
            onNodeWithContentDescription(label = "Add group").assertIsDisplayed()
            onNodeWithText(text = "New group").assertDoesNotExist()
        }
    }

    private fun groupRow(name: String): SemanticsMatcher {
        return hasText(text = name) and
            SemanticsMatcher.keyIsDefined(key = SemanticsProperties.Selected)
    }

    private suspend fun SkikoComposeUiTest.waitForNode(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher = matcher).fetchSemanticsNodes().isNotEmpty() }
    }
}
