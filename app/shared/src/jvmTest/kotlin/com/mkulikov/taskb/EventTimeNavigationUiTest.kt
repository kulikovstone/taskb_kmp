package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.onDay
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class EventTimeNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `timed events persist and stay in the calendar while all occupied days show the event`() {
        val database = directory.root.toPath().resolve("task-times.db")
        val start = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault()).yearMonth.onDay(day = 6)
        withAppKoin(file = database) { app ->
            val repository = app.koin.get<IEventRepository>()
            runBlocking {
                app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false)
                assertTrue(
                    actual = repository.save(
                        event = Event(
                            name = "Business trip",
                            dueDate = start,
                            startTime = LocalTime(hour = 18, minute = 0),
                            durationMinutes = 3_840,
                        )
                    )
                )
                assertTrue(
                    actual = repository.save(
                        event = Event(
                            name = "Team sync",
                            dueDate = start,
                            startTime = LocalTime(hour = 12, minute = 0),
                            durationMinutes = 60,
                        )
                    )
                )
                assertTrue(
                    actual = repository.save(
                        event = Event(
                            name = "Coffee break",
                            dueDate = start,
                            startTime = LocalTime(hour = 15, minute = 0),
                            durationMinutes = 30,
                        )
                    )
                )
                assertTrue(
                    actual = app.koin
                        .get<ITaskRepository>()
                        .save(task = Task(name = "Pay utility bills", dueDate = start))
                )
            }
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitText(text = "Tasks")
                onNodeWithText(text = "Tasks").performClick()
                waitText(text = "Pay utility bills")
                onNodeWithText(text = "1 task").assertIsDisplayed()
                onNodeWithText(text = "Business trip").assertDoesNotExist()
                onNodeWithText(text = "Team sync").assertDoesNotExist()
                onNodeWithText(text = "Coffee break").assertDoesNotExist()
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                onNodeWithTag(testTag = "calendar-day-number-$start", useUnmergedTree = true).performClick()
                waitText(text = "Events · 3")
                onNodeWithText(text = "Tasks · 1").assertIsDisplayed()
                val sidebar = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list"))
                for (name in listOf("Business trip", "Team sync", "Coffee break", "Pay utility bills")) {
                    onNode(matcher = hasText(text = name) and sidebar, useUnmergedTree = true).assertIsDisplayed()
                }
                onNodeWithTag(testTag = "calendar-event-time-1", useUnmergedTree = true)
                    .assertTextEquals(
                        "6 ${shortMonth(date = start)}, 18:00–9 ${shortMonth(date = start)}, 10:00 (2 days 16 hr)"
                    )
                onNode(
                        matcher = hasContentDescription(value = "Complete Team sync") and sidebar,
                        useUnmergedTree = true,
                    )
                    .assertDoesNotExist()
                onNode(
                        matcher = hasContentDescription(value = "Complete Coffee break") and sidebar,
                        useUnmergedTree = true,
                    )
                    .assertDoesNotExist()
                for (offset in 0..3) {
                    val day = start.plus(value = offset, unit = DateTimeUnit.DAY)
                    onNode(
                            matcher = hasTestTag(testTag = "calendar-event:1") and
                                hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$day")),
                            useUnmergedTree = true,
                        )
                        .assertIsDisplayed()
                }
                screenshot(name = "task-time-calendar-integration")
                onNode(matcher = hasText(text = "Team sync") and sidebar, useUnmergedTree = true).performClick()
                waitText(text = "Edit item")
                onNodeWithContentDescription(label = "Event name").performTextReplacement(text = "Updated event")
                onNodeWithText(text = "30 min").performScrollTo().performClick()
                onNodeWithText(text = "Save changes").performClick()
                waitText(text = "Updated event")
                assertEquals(
                    expected = LocalTime(hour = 12, minute = 0),
                    actual = runBlocking { repository.getById(id = 2) }?.startTime,
                )
                assertEquals(expected = 30, actual = runBlocking { repository.getById(id = 2) }?.durationMinutes)
            }
        }
        withAppKoin(file = database) { app ->
            val saved = runBlocking { app.koin.get<IEventRepository>().getById(id = 1) }
            assertEquals(expected = LocalTime(hour = 18, minute = 0), actual = saved?.startTime)
            assertEquals(expected = 3_840, actual = saved?.durationMinutes)
        }
    }

    private fun shortMonth(date: LocalDate): String {
        return date.month.name.lowercase().replaceFirstChar(
            transform = Char::uppercase,
        ).take(n = 3)
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("$name.png").toFile())
    }
}
