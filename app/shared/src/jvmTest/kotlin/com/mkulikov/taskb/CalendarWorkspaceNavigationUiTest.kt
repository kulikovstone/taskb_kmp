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
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class CalendarWorkspaceNavigationUiTest {
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
                waitForText(value = "Calendar")
                onAllNodes(matcher = hasText(text = "Calendar")).assertCountEquals(expectedSize = 1)
                onRoot().saveScreenshot(name = "home-calendar-2")
                onNodeWithText(text = "Calendar").performClick()
                onNodeWithTag(testTag = "calendar-screen").assertIsDisplayed()
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
                    hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-filters"))
                onNode(matcher = workFilter).performClick()
                onNodeWithContentDescription(label = "Close filters").performClick()
                onNodeWithText(text = "Future work").assertIsDisplayed()
                onNodeWithContentDescription(label = "Filters").performClick()
                onNodeWithText(text = "No date").performClick()
                onNode(matcher = workFilter).performClick()
                onNodeWithTag(testTag = "calendar-filters").saveScreenshot(name = "calendar-filters")
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
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-task-list")),
                    timeoutMillis = 5_000,
                )

                onNodeWithContentDescription(label = "Add item").performClick()
                waitForText(value = "New item")
                onNodeWithText(text = "Task").assertIsSelected()
                onNodeWithText(text = "Options").assertDoesNotExist()
                onNodeWithText(text = "Show in calendar").assertDoesNotExist()
                onNodeWithText(text = "Quick delete").assertDoesNotExist()
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
                onNodeWithText(text = "Quick delete").assertIsOff().performClick().assertIsOn()
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
                assertFalse(actual = createdTask.quickDeleteOverdue)
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
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar")),
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
                    hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar"))
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

    @Test
    fun `hidden weekly task appears in the month grid after moving it to yesterday`() {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(timeZone = zone)
        val yesterday = today.minus(value = 1, unit = DateTimeUnit.DAY)
        val name = "Dust the shelves"
        val yesterdayLabel = LocalDate.Format {
            monthName(names = MonthNames.ENGLISH_FULL)
            chars(value = " ")
            day(padding = Padding.NONE)
            chars(value = ", ")
            year()
        }.format(value = yesterday)
        withAppKoin(file = directory.root.toPath().resolve("overdue-weekly-task.db")) { app ->
            val tasks = app.koin.get<ITaskRepository>()
            runBlocking {
                app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false)
                assertTrue(
                    actual = tasks.save(
                        task = Task(
                            name = name,
                            dueDate = today,
                            recurrence = TaskRecurrence(
                                frequency = RecurrenceFrequency.Weekly,
                                weekdays = setOf(element = today.dayOfWeek),
                            ),
                            showInCalendar = false,
                        ),
                    ),
                )
            }
            runSkikoComposeUiTest(size = Size(width = 1900f, height = 1180f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitForText(value = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                val sidebar = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar"))
                val sidebarTask = hasText(text = name) and sidebar
                waitUntilExactlyOneExists(matcher = sidebarTask, timeoutMillis = 5_000)
                val todayCell = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$today"))
                onNode(matcher = hasText(text = name) and todayCell, useUnmergedTree = true).assertDoesNotExist()

                onNode(matcher = sidebarTask).performClick()
                waitForText(value = "Edit item")
                val dateField = hasContentDescription(value = "Choose date")
                waitUntilExactlyOneExists(matcher = dateField, timeoutMillis = 5_000)
                onNode(matcher = dateField).performClick()
                if (yesterday.yearMonth != today.yearMonth) {
                    val datePicker = hasAnyAncestor(matcher = hasTestTag(testTag = "date-picker-panel"))
                    onNode(matcher = hasContentDescription(value = "Previous month") and datePicker).performClick()
                }
                onNodeWithContentDescription(label = "Choose $yesterdayLabel").performClick()
                onNodeWithText(text = "Save changes").performClick()
                waitForText(value = "Move repeating task")
                onNodeWithText(text = "Only this event").performClick()
                waitUntilExactlyOneExists(matcher = hasTestTag(testTag = "calendar-screen"), timeoutMillis = 5_000)

                if (yesterday.yearMonth != today.yearMonth) {
                    onNodeWithContentDescription(label = "Previous month").performClick()
                }
                val yesterdayCell = hasTestTag(testTag = "calendar-day-$yesterday")
                onNode(matcher = yesterdayCell).performClick()
                waitUntilExactlyOneExists(matcher = sidebarTask, timeoutMillis = 5_000)
                val inYesterday = hasAnyAncestor(matcher = yesterdayCell)
                onNode(matcher = hasText(text = name) and inYesterday, useUnmergedTree = true).assertIsDisplayed()
                onNode(
                    matcher = hasContentDescription(value = "Complete $name") and inYesterday,
                    useUnmergedTree = true,
                ).performClick()
                waitUntilExactlyOneExists(
                    matcher = hasContentDescription(value = "Mark $name incomplete") and sidebar,
                    timeoutMillis = 5_000,
                )
                val saved = runBlocking {
                    tasks.observeCalendar(from = yesterday, through = yesterday).first().single()
                }
                val completedAt = assertNotNull(actual = saved.completedAt)
                assertEquals(expected = yesterday, actual = completedAt.toLocalDateTime(timeZone = zone).date)
                onNode(matcher = hasText(text = name) and inYesterday, useUnmergedTree = true).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `quick delete is available for incomplete recurring occurrences regardless of date`() {
        val today = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault())
        val yesterday = today.minus(value = 1, unit = DateTimeUnit.DAY)
        val tomorrow = today.plus(value = 1, unit = DateTimeUnit.DAY)
        val name = "Daily routine"
        val quickDelete = "Quick delete"
        val deleteDescription = "Delete occurrence of $name"
        withAppKoin(file = directory.root.toPath().resolve("quick-delete.db")) { app ->
            runBlocking { app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false) }
            val repository = app.koin.get<ITaskRepository>()
            runSkikoComposeUiTest(size = Size(width = 1900f, height = 1180f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitForText(value = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                if (yesterday.yearMonth != today.yearMonth) {
                    onNodeWithContentDescription(label = "Previous month").performClick()
                }
                val pastDay = hasTestTag(testTag = "calendar-day-$yesterday")
                onNode(matcher = pastDay).performClick()
                waitUntilExactlyOneExists(matcher = pastDay and isSelected(), timeoutMillis = 5_000)
                onNodeWithContentDescription(label = "Add item").performClick()
                waitForText(value = "New item")
                onNodeWithContentDescription(label = "Task name").performTextInput(text = name)
                onNodeWithContentDescription(label = "Choose repeat pattern").performClick()
                onNodeWithText(text = "Daily").performClick()
                onNodeWithText(text = quickDelete).assertIsOff().performClick().assertIsOn()
                onRoot().saveScreenshot(name = "task-creation-quick-delete")
                onNodeWithText(text = "Create item").performClick()
                val action = hasContentDescription(value = deleteDescription)
                waitUntil(timeoutMillis = 5_000) { onAllNodes(matcher = action).fetchSemanticsNodes().size == 1 }

                val taskRow = hasText(text = name) and
                    hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar"))
                onNode(matcher = taskRow).performClick()
                waitForText(value = "Edit item")
                waitForText(value = name)
                onNodeWithText(text = quickDelete).assertIsOn()
                onNodeWithContentDescription(label = "Task description").performTextInput(text = "Repeat daily")
                onNodeWithText(text = "Save changes").performClick()
                waitUntilExactlyOneExists(matcher = taskRow, timeoutMillis = 5_000)

                onNodeWithContentDescription(label = "Clear date filter").performClick()
                onNodeWithContentDescription(label = "Search tasks").performTextInput(text = name)
                val sidebar = onNodeWithTag(testTag = "calendar-sidebar")
                val todayTask = runBlocking {
                    repository.observeCalendar(from = today, through = today).first().single()
                }
                val todayAction = action and hasAnyAncestor(
                    matcher = hasTestTag(testTag = "calendar-list-task-${todayTask.key}"),
                )
                waitUntilExactlyOneExists(matcher = todayAction, timeoutMillis = 5_000)
                sidebar.saveScreenshot(name = "calendar-2-quick-delete")
                onNode(matcher = todayAction).performClick()
                waitForText(value = "Delete task occurrence?")
                onNodeWithText(text = "Only the occurrence of “$name” on", substring = true).assertIsDisplayed()
                onNodeWithTag(testTag = "taskb-dialog-panel")
                    .saveScreenshot(name = "calendar-2-quick-delete-confirmation")
                onNodeWithText(text = "Cancel").performClick()
                waitUntilDoesNotExist(matcher = hasText(text = "Delete task occurrence?"), timeoutMillis = 5_000)
                onNode(matcher = todayAction).assertIsDisplayed().performClick()
                waitForText(value = "Delete task occurrence?")
                onNodeWithText(text = "Delete").performClick()
                waitUntilDoesNotExist(matcher = hasText(text = "Delete task occurrence?"), timeoutMillis = 5_000)
                waitUntilDoesNotExist(matcher = todayAction, timeoutMillis = 5_000)
                val remaining = runBlocking { repository.observeCalendar(from = yesterday, through = tomorrow).first() }
                assertEquals(
                    expected = setOf(yesterday, tomorrow),
                    actual = remaining.mapNotNull(transform = Task::dueDate).toSet(),
                )
                val nextTask = remaining.single { task -> task.dueDate == tomorrow }
                assertTrue(actual = nextTask.quickDelete)
                val nextAction = action and hasAnyAncestor(
                    matcher = hasTestTag(testTag = "calendar-list-task-${nextTask.key}"),
                )
                waitUntilExactlyOneExists(matcher = nextAction, timeoutMillis = 5_000)

                onNodeWithContentDescription(label = "Search tasks").performTextClearance()
                onNode(matcher = pastDay).performClick()
                val inSidebar = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar"))
                val complete = hasContentDescription(value = "Complete $name") and inSidebar
                val completed = hasContentDescription(value = "Mark $name incomplete") and inSidebar
                waitUntilExactlyOneExists(matcher = pastDay and isSelected(), timeoutMillis = 5_000)
                waitUntilExactlyOneExists(matcher = complete, timeoutMillis = 5_000)
                onNode(matcher = complete).performClick()
                waitUntilExactlyOneExists(matcher = completed, timeoutMillis = 5_000)
                onNode(matcher = action and inSidebar).assertDoesNotExist()

                onNodeWithContentDescription(label = "Clear date filter").performClick()
                val pastTask = remaining.single { task -> task.dueDate == yesterday }
                val pastTaskRow = hasAnyAncestor(
                    matcher = hasTestTag(testTag = "calendar-list-task-${pastTask.key}"),
                )
                waitUntilExactlyOneExists(matcher = completed and pastTaskRow, timeoutMillis = 5_000)
                onNode(matcher = action and pastTaskRow).assertDoesNotExist()
                onNode(matcher = completed and pastTaskRow).performClick()
                waitUntilExactlyOneExists(matcher = action and pastTaskRow, timeoutMillis = 5_000)
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
