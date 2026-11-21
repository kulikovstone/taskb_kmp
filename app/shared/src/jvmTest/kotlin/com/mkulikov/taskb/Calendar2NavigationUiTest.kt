package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
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
                waitForText(value = "Edit item")
                waitForText(value = "Undated work")
                onNodeWithTag(testTag = "item-editor").assertIsDisplayed()
                onNodeWithText(text = "Task").assertIsSelected().assertIsNotEnabled()
                onNodeWithText(text = "Event").assertIsNotEnabled()
                onNodeWithText(text = "Delete").assertIsDisplayed()
                onNodeWithText(text = "Save changes").assertIsNotEnabled()
                onRoot().saveScreenshot(name = "calendar-2-edit-task")
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Updated work")
                onNodeWithText(text = "Save changes").performClick()
                waitUntilExactlyOneExists(
                    matcher = hasText(text = "Updated work") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-2-task-list")),
                    timeoutMillis = 5_000,
                )

                onNodeWithContentDescription(label = "Add item").performClick()
                waitForText(value = "New item")
                onNodeWithText(text = "Task").assertIsSelected()
                onNodeWithText(text = "Options").assertDoesNotExist()
                onNodeWithText(text = "Show in calendar").assertDoesNotExist()
                onNodeWithText(text = "Create item").assertIsNotEnabled()
                onRoot().saveScreenshot(name = "task-creation-empty")
                onNodeWithContentDescription(label = "Task name").performTextInput(text = "Added from calendar")
                onNodeWithText(text = "Event").performClick().assertIsSelected()
                onNodeWithContentDescription(label = "Event name").assertTextContains(value = "Added from calendar")
                onNodeWithText(text = "Task").performClick()
                onNodeWithText(text = "Today").performClick()
                onNodeWithContentDescription(label = "Choose repeat pattern").performClick()
                onNodeWithText(text = "Options").assertIsDisplayed()
                onNodeWithText(text = "Show in calendar").assertIsOn()
                onRoot().saveScreenshot(name = "task-creation-repeat")
                onNodeWithText(text = "Show in calendar").performClick()
                onNodeWithText(text = "Show in calendar").assertIsOff()
                onNodeWithContentDescription(label = "Clear repeat pattern").performClick()
                waitUntilDoesNotExist(matcher = hasText(text = "Options"), timeoutMillis = 5_000)
                onNodeWithText(text = "Show in calendar").assertDoesNotExist()
                onNodeWithContentDescription(label = "Clear date").performClick()
                onNodeWithText(text = "Create item").performClick()
                waitForText(value = "Added from calendar")
                val createdTask = assertNotNull(actual = runBlocking { db.taskDao().getById(id = laterTaskId + 1) })
                assertEquals(expected = groupId, actual = createdTask.groupId)
                assertTrue(actual = createdTask.showInCalendar)
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

                onNodeWithContentDescription(label = "Add item").performClick()
                waitForText(value = "New item")
                onNodeWithContentDescription(label = "Task name").performTextInput(text = "Event from calendar")
                onNodeWithContentDescription(label = "Task description").performTextInput(text = "Agenda")
                onNodeWithText(text = "Event").performClick()
                onNodeWithContentDescription(label = "Event name").assertTextContains(value = "Event from calendar")
                onNodeWithContentDescription(label = "Event description").assertTextContains(value = "Agenda")
                onNodeWithContentDescription(label = "Choose group").performClick()
                waitForText(value = "Choose group")
                onNodeWithText(text = "Work").performClick()
                waitForText(value = "New item")
                onNodeWithContentDescription(label = "Choose start time").performClick()
                onNodeWithContentDescription(label = "Apply time").performClick()
                onNodeWithText(text = "1 hour").performClick()
                onNodeWithContentDescription(label = "Choose repeat pattern").performClick()
                onNodeWithText(text = "Weekly").performClick()
                onRoot().saveScreenshot(name = "task-creation-event")
                onNodeWithText(text = "Options").assertDoesNotExist()
                onNodeWithText(text = "Create item").performClick()
                val eventRow = hasText(text = "Event from calendar") and
                    hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-2-sidebar"))
                waitUntilExactlyOneExists(matcher = eventRow, timeoutMillis = 5_000)
                val events = app.koin.get<IEventRepository>()
                val event = runBlocking { events.events.first().single() }
                assertEquals(expected = today, actual = event.dueDate)
                assertEquals(expected = "Agenda", actual = event.description)
                assertEquals(expected = groupId, actual = event.groupId)
                assertEquals(expected = 60, actual = event.durationMinutes)
                assertNotNull(actual = event.recurrence)

                onNode(matcher = eventRow).performClick()
                waitForText(value = "Edit item")
                waitForText(value = event.name)
                onNodeWithTag(testTag = "item-editor").assertIsDisplayed()
                onNodeWithText(text = "Event").assertIsSelected().assertIsNotEnabled()
                onNodeWithText(text = "Task").assertIsNotEnabled()
                onNodeWithContentDescription(label = "Event description").assertTextContains(value = "Agenda")
                onNodeWithText(text = "Work").assertIsDisplayed()
                onNodeWithText(text = "Save changes").assertIsNotEnabled()
                onRoot().saveScreenshot(name = "calendar-2-edit-event")
                onNodeWithText(text = "30 min").performClick()
                onNodeWithText(text = "Save changes").performClick()
                waitForText(value = "Move repeating event")
                onNodeWithText(text = "Only this event").performClick()
                waitUntilExactlyOneExists(matcher = eventRow, timeoutMillis = 5_000)
                val nextWeek = today.plus(value = 7, unit = DateTimeUnit.DAY)
                val occurrences = runBlocking { events.observeCalendar(from = today, through = nextWeek).first() }
                val durations = occurrences.associate { occurrence -> occurrence.dueDate to occurrence.durationMinutes }
                assertEquals(expected = 30, actual = durations[today])
                assertEquals(expected = 60, actual = durations[nextWeek])
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
