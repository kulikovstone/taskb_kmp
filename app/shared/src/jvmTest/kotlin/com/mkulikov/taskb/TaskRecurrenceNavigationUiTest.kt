package com.mkulikov.taskb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class TaskRecurrenceNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `one series is created moved skipped renamed and deleted through the real app`() {
        withAppKoin(file = directory.root.toPath().resolve("recurrence.db")) { app ->
            val today = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault())
            val start = today.yearMonth.onDay(day = 3)
            val repo = app.koin.get<ITaskRepository>()
            val db = app.koin.get<AppDatabase>()
            runBlocking { app.koin.get<IAppSettings>().completeWelcome() }
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 1100f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitText(text = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                waitText(text = "No date")
                onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
                onNodeWithText(text = "Task").performClick()
                waitText(text = "Create task")
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Recurring routine")
                onNodeWithContentDescription(label = "Choose date").performClick()
                val dateLabel = LocalDate.Format {
                        monthName(names = MonthNames.ENGLISH_FULL)
                        chars(value = " ")
                        day(padding = Padding.NONE)
                        chars(value = ", ")
                        year()
                    }
                    .format(value = start)
                onNodeWithContentDescription(label = "Choose $dateLabel").performClick()
                onNodeWithContentDescription(label = "Choose repeat pattern").performScrollTo().performClick()
                waitText(text = "Every month on day 3")
                onNodeWithText(text = "Daily").performClick()
                waitText(text = "Every day")
                onNodeWithText(text = "After").performScrollTo().performClick()
                onNodeWithContentDescription(label = "Repeat occurrence count")
                    .performScrollTo()
                    .performTextReplacement(text = "3")
                onNodeWithContentDescription(label = "Save task").performClick()
                waitText(text = "No date")
                var events = runBlocking {
                    repo
                        .observeCalendar(from = start, through = start.plus(value = 10, unit = DateTimeUnit.DAY))
                        .first()
                        .sortedBy(selector = Task::dueDate)
                }
                assertEquals(expected = 3, actual = events.size)
                assertEquals(expected = 1, actual = runBlocking { db.taskDao().observeTasks().first().size })
                val first = events[0]
                val second = events[1]
                val third = events[2]
                val sidebar = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list"))
                fun sidebarTask(task: Task) = hasTestTag(testTag = "calendar-task-${task.key}") and sidebar

                onNodeWithTag(testTag = "calendar-day-number-$start", useUnmergedTree = true).performClick()
                waitText(text = "Daily · 1")
                onNode(matcher = sidebarTask(task = first), useUnmergedTree = true).assertIsDisplayed()
                for (task in events) {
                    val date = requireNotNull(value = task.dueDate)
                    val cellTask = onNode(
                        matcher = hasTestTag(testTag = "calendar-task-${task.key}") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$date")),
                        useUnmergedTree = true,
                    )
                    if (date < today) {
                        cellTask.assertIsDisplayed()
                    } else {
                        cellTask.assertDoesNotExist()
                    }
                }
                onNode(
                        matcher = hasContentDescription(value = "Complete Recurring routine") and
                            hasAnyAncestor(matcher = sidebarTask(task = first)),
                        useUnmergedTree = true,
                    )
                    .performClick()
                waitUntil(timeoutMillis = 10_000) {
                    runBlocking {
                        repo.getOccurrence(id = first.id, occurrence = requireNotNull(value = first.occurrenceId))
                    }
                        ?.isCompleted == true
                }
                onNodeWithTag(testTag = "calendar-day-number-${second.dueDate}", useUnmergedTree = true)
                    .performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(
                            matcher = sidebarTask(task = second),
                            useUnmergedTree = true,
                        )
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                val source = onNode(matcher = sidebarTask(task = second), useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .center
                val targetDate = start.plus(value = 3, unit = DateTimeUnit.DAY)
                val target = onNodeWithTag(testTag = "calendar-day-$targetDate", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .center
                onRoot().performMouseInput {
                    moveTo(position = source)
                    press()
                    moveTo(position = source + Offset(x = 28f, y = 0f))
                    moveTo(position = target)
                    release()
                }
                waitText(text = "Move repeating task")
                onNodeWithText(text = "Only this event").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    runBlocking {
                        repo.getOccurrence(id = second.id, occurrence = requireNotNull(value = second.occurrenceId))
                    }
                        ?.dueDate == targetDate
                }
                onNodeWithTag(testTag = "calendar-day-number-$targetDate", useUnmergedTree = true).performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(
                            matcher = sidebarTask(task = second),
                            useUnmergedTree = true,
                        )
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNode(matcher = sidebarTask(task = second), useUnmergedTree = true).performClick()
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Delete task").performClick()
                waitText(text = "Delete repeating task")
                onNodeWithText(text = "Only this event").performClick()
                waitText(text = "No date")
                assertEquals(
                    expected = 2,
                    actual = runBlocking {
                        repo
                            .observeCalendar(
                                from = start,
                                through = start.plus(value = 10, unit = DateTimeUnit.DAY),
                            )
                            .first()
                            .size
                    },
                )
                onNodeWithTag(testTag = "calendar-day-number-${third.dueDate}", useUnmergedTree = true)
                    .performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = sidebarTask(task = third), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNode(matcher = sidebarTask(task = third), useUnmergedTree = true).performClick()
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Renamed series")
                onNodeWithContentDescription(label = "Save task").performClick()
                waitText(text = "No date")
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasText(text = "Renamed series") and sidebar, useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .size == 1
                }
                val output = Path.of("build/reports/screenshots/recurrence-calendar.png")
                Files.createDirectories(output.parent)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                events = runBlocking {
                    repo
                        .observeCalendar(from = start, through = start.plus(value = 10, unit = DateTimeUnit.DAY))
                        .first()
                }
                assertTrue(actual = events.all { task -> task.name == "Renamed series" })
                assertEquals(expected = 1, actual = events.count(predicate = Task::isCompleted))
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "Tasks")
                onNodeWithText(text = "Tasks").performClick()
                waitText(text = "Renamed series")
                assertEquals(
                    expected = 1,
                    actual = onAllNodes(matcher = hasText(text = "Renamed series")).fetchSemanticsNodes().size,
                )
                onNodeWithText(text = "Renamed series").performClick()
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Delete task").performClick()
                waitText(text = "Delete repeating task")
                onNodeWithText(text = "Entire task and all events").performClick()
                waitText(text = "No tasks yet.")
                assertTrue(actual = runBlocking { db.taskDao().observeRecords().first().isEmpty() })
            }
        }
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
