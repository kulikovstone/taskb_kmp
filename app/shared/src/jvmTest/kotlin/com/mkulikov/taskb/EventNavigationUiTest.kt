package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class EventNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `event editor opens closes and reopens with equivalent fields in wide and compact layouts and both themes`() {
        for (width in listOf(1280, 400)) for (dark in listOf(false, true)) {
            val suffix = "$width-${if (dark) "dark" else "light"}"
            withAppKoin(directory.root.toPath().resolve("event-$suffix.db")) { app ->
                val navigator = app.koin.get<IAppNavigator>()
                runSkikoComposeUiTest(size = Size(width.toFloat(), 1000f)) {
                    setContent { KoinIsolatedContext(app) { TaskBTheme(dark) { AppRoot(HomeRoute) } } }
                    val home = hasText("Tasks") and hasClickAction()
                    onNode(home).assertIsDisplayed()
                    repeat(2) { opening ->
                        runOnIdle { navigator.navigate(EventEditorRoute.Create(LocalDate(2026, 11, 5))) }
                        waitText("New event")
                        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription("Event name")).fetchSemanticsNodes().isNotEmpty() }
                        onNodeWithContentDescription("Save event").assertIsNotEnabled()
                        onNodeWithContentDescription("Event name").performTextInput("Draft event")
                        onNodeWithContentDescription("Event description").assertIsDisplayed()
                        onNodeWithContentDescription("Choose event group").performScrollTo().assertIsDisplayed()
                        onNodeWithContentDescription("Choose start time").performScrollTo().assertIsDisplayed()
                        onNodeWithContentDescription("Choose repeat pattern").performScrollTo().assertIsDisplayed()
                        onNodeWithContentDescription("Save event").assertIsEnabled()
                        if (opening == 0) {
                            onNodeWithContentDescription("Event name").performScrollTo()
                            screenshot("event-create-$suffix")
                        }
                        onNodeWithContentDescription("Back").performClick()
                        waitText("Tasks")
                        onNodeWithText("New event").assertDoesNotExist()
                    }
                }
                assertTrue(runBlocking { app.koin.get<IEventRepository>().events.first() }.isEmpty())
            }
        }
    }

    @Test
    fun `calendar creates events with selected date group timing and recurrence then edits moves and deletes them without completion`() {
        val file = directory.root.toPath().resolve("calendar-events.db")
        withAppKoin(file) { app ->
            val db = app.koin.get<AppDatabase>()
            val events = app.koin.get<IEventRepository>()
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val date = today.yearMonth.onDay(3)
            runBlocking { db.groupDao().insert(GroupEntity(name = "Work", color = Palette.Blue.s400.toArgb())) }
            runSkikoComposeUiTest(size = Size(1440f, 1100f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { AppRoot(CalendarRoute) } } }
                waitText("Event")
                val taskButton = onNodeWithText("Task").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val eventButton = onNodeWithText("Event").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(eventButton.left > taskButton.right)
                onNodeWithText("New task").assertDoesNotExist()
                onNodeWithTag("calendar-day-number-$date", useUnmergedTree = true).performClick()
                onNodeWithText("Event").performClick()
                waitText("Create event")
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription("Event name")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Event name").performTextInput("Team meeting")
                onNodeWithContentDescription("Event description").performTextInput("Discuss the release")
                onNodeWithContentDescription("Choose event group").performClick()
                waitText("Work")
                onNodeWithText("Work").performClick()
                waitText("Create event")
                onNodeWithContentDescription("Choose start time").performScrollTo().performClick()
                waitText("Choose start time")
                onNodeWithContentDescription("Apply time").performClick()
                onNodeWithContentDescription("Choose repeat pattern").performScrollTo().performClick()
                onNodeWithText("Daily").performScrollTo().performClick()
                onNodeWithText("After").performScrollTo().performClick()
                onNodeWithContentDescription("Repeat occurrence count").performScrollTo().performTextReplacement("3")
                onNodeWithContentDescription("Save event").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("calendar-event:1:1:$date"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                val saved = runBlocking { events.observeCalendar(date, date).first().single() }
                assertEquals(date, saved.dueDate)
                assertEquals(LocalTime(9, 0), saved.startTime)
                assertEquals(60, saved.durationMinutes)
                assertEquals(1, saved.groupId)
                assertEquals(3, saved.recurrence?.occurrenceCount)
                assertEquals("Discuss the release", saved.description)
                onNodeWithContentDescription("Complete Team meeting").assertDoesNotExist()
                onNodeWithText("Daily · 1").assertDoesNotExist()
                onNode(hasText("Team meeting") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).performClick()
                waitText("Edit event")
                onNodeWithContentDescription("Event description").assertTextEquals("Discuss the release")
                onNodeWithContentDescription("Event name").performTextReplacement("Updated meeting")
                onNodeWithContentDescription("Save event").performClick()
                waitText("Updated meeting")
                val source = onNode(hasText("Updated meeting") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot.center
                val targetDate = date.plus(7, DateTimeUnit.DAY)
                val target = onNodeWithTag("calendar-day-$targetDate", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                onRoot().performMouseInput { moveTo(source); press(); moveTo(source + Offset(28f, 0f)); moveTo(target); release() }
                waitText("Move repeating event")
                onNodeWithText("Only this event").performClick()
                waitText("Undo")
                assertEquals(targetDate, runBlocking { events.getOccurrence(saved.id, requireNotNull(saved.occurrenceId)) }?.dueDate)
                onNodeWithText("Undo").performClick()
                waitUntil(timeoutMillis = 10_000) { runBlocking { events.getOccurrence(saved.id, requireNotNull(saved.occurrenceId)) }?.dueDate == date }
                onNode(hasText("Updated meeting") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).performClick()
                waitText("Edit event")
                onNodeWithContentDescription("Delete event").performClick()
                waitText("Delete repeating event")
                onNodeWithText("Entire event and all occurrences").performClick()
                waitText("Event")
                waitUntil(timeoutMillis = 10_000) { runBlocking { events.events.first() }.isEmpty() }
                assertNull(runBlocking { db.taskDao().getById(1) })
                onNodeWithText("Event").performClick()
                waitText("Create event")
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription("Event name")).fetchSemanticsNodes().isNotEmpty() }
                onNode(hasContentDescription("Choose event group") and hasText("Work")).assertIsDisplayed()
                onNodeWithContentDescription("Event name").performTextInput("All-day event")
                onNodeWithContentDescription("Save event").performClick()
                waitText("All-day event")
                onNodeWithContentDescription("Complete All-day event").assertDoesNotExist()
                screenshot("calendar-events")
            }
        }
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun SkikoComposeUiTest.screenshot(name: String) {
        val output = Path.of("build/reports/screenshots/$name.png")
        Files.createDirectories(output.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
    }
}
