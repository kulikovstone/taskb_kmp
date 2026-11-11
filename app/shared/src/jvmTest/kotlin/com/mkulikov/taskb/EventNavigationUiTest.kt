package com.mkulikov.taskb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.navigation.AppRoot
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.feature.calendar.api.CalendarRoute
import com.mkulikov.taskb.feature.event.api.EventEditorRoute
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.home.api.HomeRoute
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class EventNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `event editor opens closes and reopens with equivalent fields in wide and compact layouts and both themes`() {
        for (width in listOf(1280, 400)) for (dark in listOf(false, true)) {
            val suffix = "$width-${if (dark) "dark" else "light"}"
            withAppKoin(file = directory.root.toPath().resolve("event-$suffix.db")) { app ->
                val navigator = app.koin.get<IAppNavigator>()
                runSkikoComposeUiTest(size = Size(width = width.toFloat(), height = 1000f)) {
                    setContent {
                        KoinIsolatedContext(context = app) {
                            TaskBTheme(darkTheme = dark) { AppRoot(startDestination = HomeRoute) }
                        }
                    }
                    val home = hasText(text = "Tasks") and hasClickAction()
                    onNode(matcher = home).assertIsDisplayed()
                    repeat(times = 2) { opening ->
                        runOnIdle {
                            navigator.navigate(
                                key = EventEditorRoute.Create(
                                    initialDate = LocalDate(year = 2026, month = 11, day = 5)
                                )
                            )
                        }
                        waitText(text = "New event")
                        waitUntil(timeoutMillis = 10_000) {
                            onAllNodes(matcher = hasContentDescription(value = "Event name"))
                                .fetchSemanticsNodes()
                                .isNotEmpty()
                        }
                        onNodeWithContentDescription(label = "Save event").assertIsNotEnabled()
                        onNodeWithContentDescription(label = "Event name").performTextInput(text = "Draft event")
                        onNodeWithContentDescription(label = "Event description").assertIsDisplayed()
                        onNodeWithContentDescription(label = "Choose event group")
                            .performScrollTo()
                            .assertIsDisplayed()
                        onNodeWithContentDescription(label = "Choose start time")
                            .performScrollTo()
                            .assertIsDisplayed()
                        onNodeWithContentDescription(label = "Choose repeat pattern")
                            .performScrollTo()
                            .assertIsDisplayed()
                        onNodeWithContentDescription(label = "Save event").assertIsEnabled()
                        if (opening == 0) {
                            onNodeWithContentDescription(label = "Event name").performScrollTo()
                            screenshot(name = "event-create-$suffix")
                        }
                        onNodeWithContentDescription(label = "Back").performClick()
                        waitText(text = "Tasks")
                        onNodeWithText(text = "New event").assertDoesNotExist()
                    }
                }
                assertTrue(actual = runBlocking { app.koin.get<IEventRepository>().events.first() }.isEmpty())
            }
        }
    }

    @Test
    fun `calendar creates edits moves and deletes timed recurring events without completion`() {
        val file = directory.root.toPath().resolve("calendar-events.db")
        withAppKoin(file = file) { app ->
            val db = app.koin.get<AppDatabase>()
            val events = app.koin.get<IEventRepository>()
            val today = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault())
            val date = today.yearMonth.onDay(day = 3)
            runBlocking {
                db.groupDao().insert(group = GroupEntity(name = "Work", color = Palette.Blue.s400.toArgb()))
            }
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 1100f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { AppRoot(startDestination = CalendarRoute) }
                    }
                }
                waitText(text = "Event")
                val taskButton = onNodeWithText(text = "Task").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val eventButton = onNodeWithText(text = "Event").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(actual = eventButton.left > taskButton.right)
                onNodeWithText(text = "New task").assertDoesNotExist()
                onNodeWithTag(testTag = "calendar-day-number-$date", useUnmergedTree = true).performClick()
                onNodeWithText(text = "Event").performClick()
                waitText(text = "Create event")
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasContentDescription(value = "Event name"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Event name").performTextInput(text = "Team meeting")
                onNodeWithContentDescription(label = "Event description")
                    .performTextInput(text = "Discuss the release")
                onNodeWithContentDescription(label = "Choose event group").performClick()
                waitText(text = "Work")
                onNodeWithText(text = "Work").performClick()
                waitText(text = "Create event")
                onNodeWithContentDescription(label = "Choose start time").performScrollTo().performClick()
                waitText(text = "Choose time")
                onNodeWithContentDescription(label = "Apply time").performClick()
                onNodeWithContentDescription(label = "Choose repeat pattern").performScrollTo().performClick()
                onNodeWithText(text = "Daily").performScrollTo().performClick()
                onNodeWithText(text = "After").performScrollTo().performClick()
                onNodeWithContentDescription(label = "Repeat occurrence count")
                    .performScrollTo()
                    .performTextReplacement(text = "3")
                onNodeWithContentDescription(label = "Save event").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasTestTag(testTag = "calendar-event:1:1:$date"), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                val saved = runBlocking { events.observeCalendar(from = date, through = date).first().single() }
                assertEquals(expected = date, actual = saved.dueDate)
                assertEquals(expected = LocalTime(hour = 9, minute = 0), actual = saved.startTime)
                assertEquals(expected = 30, actual = saved.durationMinutes)
                assertEquals(expected = 1, actual = saved.groupId)
                assertEquals(expected = 3, actual = saved.recurrence?.occurrenceCount)
                assertEquals(expected = "Discuss the release", actual = saved.description)
                onNodeWithContentDescription(label = "Complete Team meeting").assertDoesNotExist()
                onNodeWithText(text = "Recurring · 1").assertDoesNotExist()
                onNode(
                        matcher = hasText(text = "Team meeting") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .performClick()
                waitText(text = "Edit event")
                onNodeWithContentDescription(label = "Event description").assertTextEquals("Discuss the release")
                onNodeWithContentDescription(label = "Event name").performTextReplacement(text = "Updated meeting")
                onNodeWithContentDescription(label = "Save event").performClick()
                waitText(text = "Updated meeting")
                val source = onNode(
                        matcher = hasText(text = "Updated meeting") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .center
                val targetDate = date.plus(value = 7, unit = DateTimeUnit.DAY)
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
                waitText(text = "Move repeating event")
                onNodeWithText(text = "Only this event").performClick()
                waitText(text = "Undo")
                assertEquals(
                    expected = targetDate,
                    actual = runBlocking {
                            events.getOccurrence(
                                id = saved.id,
                                occurrence = requireNotNull(value = saved.occurrenceId),
                            )
                        }
                            ?.dueDate,
                )
                onNodeWithText(text = "Undo").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    runBlocking {
                        events.getOccurrence(id = saved.id, occurrence = requireNotNull(value = saved.occurrenceId))
                    }
                        ?.dueDate == date
                }
                onNode(
                        matcher = hasText(text = "Updated meeting") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .performClick()
                waitText(text = "Edit event")
                onNodeWithContentDescription(label = "Delete event").performClick()
                waitText(text = "Delete repeating event")
                onNodeWithText(text = "Entire event and all occurrences").performClick()
                waitText(text = "Event")
                waitUntil(timeoutMillis = 10_000) { runBlocking { events.events.first() }.isEmpty() }
                assertNull(actual = runBlocking { db.taskDao().getById(id = 1) })
                onNodeWithText(text = "Event").performClick()
                waitText(text = "Create event")
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasContentDescription(value = "Event name"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNode(matcher = hasContentDescription(value = "Choose event group") and hasText(text = "Work"))
                    .assertIsDisplayed()
                onNodeWithContentDescription(label = "Event name").performTextInput(text = "All-day event")
                onNodeWithContentDescription(label = "Save event").performClick()
                waitText(text = "All-day event")
                onNodeWithContentDescription(label = "Complete All-day event").assertDoesNotExist()
                screenshot(name = "calendar-events")
            }
        }
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val output = Path.of("build/reports/screenshots/$name.png")
        Files.createDirectories(output.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
    }
}
