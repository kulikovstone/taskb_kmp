package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.onDay
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class TaskTimeNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `timed tasks persist and stay in the calendar while all occupied days show the event`() {
        val database = directory.root.toPath().resolve("task-times.db")
        val start = Clock.System.todayIn(TimeZone.currentSystemDefault()).yearMonth.onDay(6)
        withAppKoin(database) { app ->
            val repository = app.koin.get<ITaskRepository>()
            runBlocking {
                app.koin.get<IAppSettings>().completeWelcome()
                assertTrue(repository.save(Task(name = "Business trip", dueDate = start,
                    startTime = LocalTime(18, 0), durationMinutes = 3_840)))
                assertTrue(repository.save(Task(name = "Team sync", dueDate = start,
                    startTime = LocalTime(12, 0), durationMinutes = 60)))
                assertTrue(repository.save(Task(name = "Coffee break", dueDate = start,
                    startTime = LocalTime(15, 0), durationMinutes = 30)))
                assertTrue(repository.save(Task(name = "Pay utility bills", dueDate = start)))
            }
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitText("Tasks")
                onNodeWithText("Tasks").performClick()
                waitText("Pay utility bills")
                onNodeWithText("1 task").assertIsDisplayed()
                onNodeWithText("Business trip").assertDoesNotExist()
                onNodeWithText("Team sync").assertDoesNotExist()
                onNodeWithText("Coffee break").assertDoesNotExist()
                onNodeWithContentDescription("Back").performClick()
                waitText("Calendar")
                onNodeWithText("Calendar").performClick()
                onNodeWithTag("calendar-day-number-$start", useUnmergedTree = true).performClick()
                waitText("With time · 3")
                onNodeWithText("Without time · 1").assertIsDisplayed()
                val sidebar = hasAnyAncestor(hasTestTag("calendar-sidebar-list"))
                for (name in listOf("Business trip", "Team sync", "Coffee break", "Pay utility bills")) {
                    onNode(hasText(name) and sidebar, useUnmergedTree = true).assertIsDisplayed()
                }
                onNodeWithTag("calendar-task-time-1", useUnmergedTree = true)
                    .assertTextEquals("6 ${shortMonth(start)}, 18:00–9 ${shortMonth(start)}, 10:00 (2 days 16 hr)")
                onNode(hasContentDescription("Complete Team sync") and sidebar, useUnmergedTree = true).assertDoesNotExist()
                onNode(hasContentDescription("Complete Coffee break") and sidebar, useUnmergedTree = true).assertDoesNotExist()
                for (offset in 0..3) {
                    val day = start.plus(offset, DateTimeUnit.DAY)
                    onNode(hasTestTag("calendar-task-1") and hasAnyAncestor(hasTestTag("calendar-day-$day")),
                        useUnmergedTree = true).assertIsDisplayed()
                }
                screenshot("task-time-calendar-integration")
                onNode(hasText("Team sync") and sidebar, useUnmergedTree = true).performClick()
                waitText("Edit task")
                onNodeWithContentDescription("Task name").performTextReplacement("Updated timed task")
                onNodeWithText("30 min").performScrollTo().performClick()
                onNodeWithContentDescription("Save task").performClick()
                waitText("Updated timed task")
                assertEquals(LocalTime(12, 0), runBlocking { repository.getById(2) }?.startTime)
                assertEquals(30, runBlocking { repository.getById(2) }?.durationMinutes)
            }
        }
        withAppKoin(database) { app ->
            val saved = runBlocking { app.koin.get<ITaskRepository>().getById(1) }
            assertEquals(LocalTime(18, 0), saved?.startTime)
            assertEquals(3_840, saved?.durationMinutes)
        }
    }

    private fun shortMonth(date: LocalDate): String = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("$name.png").toFile())
    }
}
