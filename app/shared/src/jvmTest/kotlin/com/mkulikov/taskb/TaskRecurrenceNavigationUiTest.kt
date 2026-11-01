package com.mkulikov.taskb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
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
class TaskRecurrenceNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `one series is created moved skipped renamed and deleted through the real app`() {
        withAppKoin(directory.root.toPath().resolve("recurrence.db")) { app ->
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val start = today.yearMonth.onDay(3)
            val repo = app.koin.get<ITaskRepository>()
            val db = app.koin.get<AppDatabase>()
            runBlocking { app.koin.get<IAppSettings>().completeWelcome() }
            runSkikoComposeUiTest(size = Size(1440f, 1100f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitText("Calendar")
                onNodeWithText("Calendar").performClick()
                waitText("Unscheduled")
                onNodeWithTag("calendar-day-$start", useUnmergedTree = true).performClick()
                waitText("Create task")
                onNodeWithContentDescription("Task name").performTextReplacement("Recurring routine")
                onNodeWithContentDescription("Choose repeat pattern").performScrollTo().performClick()
                waitText("Every month on day 3")
                onNodeWithText("Daily").performClick()
                waitText("Every day")
                onNodeWithText("After").performScrollTo().performClick()
                onNodeWithContentDescription("Repeat occurrence count").performScrollTo().performTextReplacement("3")
                onNodeWithContentDescription("Save task").performClick()
                waitText("Unscheduled")
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Recurring routine"), useUnmergedTree = true).fetchSemanticsNodes().size == 3 }
                var events = runBlocking { repo.observeCalendar(start, start.plus(10, DateTimeUnit.DAY)).first().sortedBy { it.dueDate } }
                assertEquals(3, events.size)
                assertEquals(1, runBlocking { db.taskDao().observeTasks().first().size })
                val first = events[0]
                val second = events[1]
                val third = events[2]
                onNode(hasContentDescription("Complete Recurring routine") and hasAnyAncestor(hasTestTag("calendar-task-${first.key}")), useUnmergedTree = true).performClick()
                waitUntil(timeoutMillis = 10_000) { runBlocking { repo.getOccurrence(first.id, requireNotNull(first.occurrenceId)) }?.isCompleted == true }
                val source = onNodeWithTag("calendar-task-${second.key}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                val targetDate = start.plus(3, DateTimeUnit.DAY)
                val target = onNodeWithTag("calendar-day-$targetDate", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                onRoot().performMouseInput { moveTo(source); press(); moveTo(source + Offset(28f, 0f)); moveTo(target); release() }
                waitText("Move repeating task")
                onNodeWithText("Only this event").performClick()
                waitUntil(timeoutMillis = 10_000) { runBlocking { repo.getOccurrence(second.id, requireNotNull(second.occurrenceId)) }?.dueDate == targetDate }
                onNodeWithTag("calendar-task-${second.key}", useUnmergedTree = true).performClick()
                waitText("Edit task")
                onNodeWithContentDescription("Delete task").performClick()
                waitText("Delete repeating task")
                onNodeWithText("Only this event").performClick()
                waitText("Unscheduled")
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Recurring routine"), useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
                onNodeWithTag("calendar-task-${third.key}", useUnmergedTree = true).performClick()
                waitText("Edit task")
                onNodeWithContentDescription("Task name").performTextReplacement("Renamed series")
                onNodeWithContentDescription("Save task").performClick()
                waitText("Unscheduled")
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Renamed series"), useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
                val output = Path.of("build/reports/screenshots/recurrence-calendar.png")
                Files.createDirectories(output.parent)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                events = runBlocking { repo.observeCalendar(start, start.plus(10, DateTimeUnit.DAY)).first() }
                assertTrue(events.all { it.name == "Renamed series" })
                assertEquals(1, events.count { it.isCompleted })
                onNodeWithContentDescription("Back").performClick()
                waitText("Tasks")
                onNodeWithText("Tasks").performClick()
                waitText("Renamed series")
                assertEquals(1, onAllNodes(hasText("Renamed series")).fetchSemanticsNodes().size)
                onNodeWithText("Renamed series").performClick()
                waitText("Edit task")
                onNodeWithContentDescription("Delete task").performClick()
                waitText("Delete repeating task")
                onNodeWithText("Entire task and all events").performClick()
                waitText("No tasks yet.")
                assertTrue(runBlocking { db.taskDao().observeRecords().first().isEmpty() })
            }
        }
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
