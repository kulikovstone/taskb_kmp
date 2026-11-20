package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class Calendar2NavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `calendar 2 manages tasks outside the month and applies both filters together`() {
        val today = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault())
        val later = today.plus(value = 3, unit = DateTimeUnit.MONTH)
        withAppKoin(file = directory.root.toPath().resolve("calendar-2.db")) { app ->
            val db = app.koin.get<AppDatabase>()
            val (groupId, laterTaskId) = runBlocking {
                app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false)
                val groupId = db.groupDao()
                    .insert(group = GroupEntity(name = "Work", color = Palette.Blue.s400.toArgb()))
                    .toInt()
                db.taskDao().insert(task = TaskEntity(name = "Today task", dueDate = today.toEpochDays()))
                db.taskDao().insert(task = TaskEntity(name = "Undated work", groupId = groupId))
                db.taskDao().insert(task = TaskEntity(name = "Undated personal"))
                val laterTaskId = db.taskDao().insert(
                    task = TaskEntity(name = "Future work", groupId = groupId, dueDate = later.toEpochDays()),
                ).toInt()
                groupId to laterTaskId
            }
            runSkikoComposeUiTest(size = Size(width = 1900f, height = 1180f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitForText(value = "Calendar 2")
                onNodeWithText(text = "Calendar").assertIsDisplayed()
                onRoot().saveScreenshot(name = "home-calendar-2")
                onNodeWithText(text = "Calendar 2").performClick()
                onNodeWithTag(testTag = "calendar-2").assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-day-$today").assertIsSelected()
                onNodeWithText(text = "Future work").assertDoesNotExist()
                onRoot().saveScreenshot(name = "calendar-2-selected-day")

                onNodeWithContentDescription(label = "Clear date filter").performClick()
                waitForText(value = "Future work")
                onRoot().saveScreenshot(name = "calendar-2-all-tasks")
                onNodeWithContentDescription(label = "Complete Future work").performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodes(matcher = hasContentDescription(value = "Mark Future work incomplete"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                assertNotNull(actual = runBlocking { db.taskDao().getById(id = laterTaskId) }?.completedAt)

                onNodeWithContentDescription(label = "Filters").performClick()
                onNodeWithText(text = "No date").performClick()
                val workFilter = hasText(text = "Work") and
                    hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-2-filters"))
                onNode(matcher = workFilter).performClick()
                onNodeWithContentDescription(label = "Close filters").performClick()
                onNodeWithText(text = "Future work").assertIsDisplayed()
                onNodeWithContentDescription(label = "Filters").performClick()
                onNodeWithText(text = "No date").performClick()
                onNode(matcher = workFilter).performClick()
                onNodeWithTag(testTag = "calendar-2-filters").saveScreenshot(name = "calendar-2-filters")
                onNodeWithText(text = "Apply").performClick()
                onNodeWithText(text = "Undated work").assertIsDisplayed()
                onNodeWithText(text = "Undated personal").assertDoesNotExist()
                onNodeWithText(text = "Future work").assertDoesNotExist()
                onNodeWithTag(testTag = "calendar-grid").assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-day-$today").assertIsNotSelected()

                onNodeWithContentDescription(label = "Search tasks").performTextInput(text = "missing")
                onNodeWithText(text = "No tasks to show.").assertIsDisplayed()
                onNodeWithContentDescription(label = "Search tasks").performTextClearance()
                onNodeWithText(text = "Undated work").performClick()
                waitForText(value = "Edit task")
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Updated work")
                onNodeWithContentDescription(label = "Save task").performClick()
                waitForText(value = "Updated work")

                onNodeWithContentDescription(label = "Add task").performClick()
                waitForText(value = "New task")
                onNodeWithContentDescription(label = "Task name").performTextInput(text = "Added from calendar")
                onNodeWithContentDescription(label = "Save task").performClick()
                waitForText(value = "Added from calendar")
                assertEquals(
                    expected = groupId,
                    actual = runBlocking { db.taskDao().getById(id = laterTaskId + 1) }?.groupId,
                )
                onNodeWithContentDescription(label = "Settings").performClick()
                waitForText(value = "Settings")
                onNodeWithContentDescription(label = "Back").performClick()
                waitForText(value = "Updated work")

                onNodeWithContentDescription(label = "Clear group filter").assertIsDisplayed()
                onNodeWithContentDescription(label = "Clear task filter").assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-day-$today").performClick().assertIsSelected()
                onNodeWithContentDescription(label = "Clear group filter").assertDoesNotExist()
                onNodeWithContentDescription(label = "Clear task filter").assertDoesNotExist()
                onNode(
                    matcher = hasText(text = "Today task") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-2-sidebar")),
                ).assertIsDisplayed()
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.waitForText(value: String) {
    waitUntil(timeoutMillis = 5_000) { onAllNodes(matcher = hasText(text = value)).fetchSemanticsNodes().isNotEmpty() }
}

@OptIn(ExperimentalTestApi::class)
private fun SemanticsNodeInteraction.saveScreenshot(name: String) {
    val path = Path.of("build/reports/screenshots", "$name.png")
    Files.createDirectories(path.parent)
    ImageIO.write(captureToImage().toAwtImage(), "png", path.toFile())
}
