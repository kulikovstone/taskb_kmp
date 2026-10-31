package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.format.MonthNames
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import kotlin.test.*
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class CalendarNavigationUiTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `calendar preserves its month and opens task creation with a date or the selected day`() {
        withAppKoin(directory.root.toPath().resolve("calendar.db")) { app ->
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val next = today.yearMonth.plusMonth()
            val label = YearMonth.Format { monthName(MonthNames.ENGLISH_FULL); chars(" "); year() }.format(next)
            val db = app.koin.get<AppDatabase>()
            runBlocking {
                app.koin.get<IAppSettings>().completeWelcome()
                db.taskDao().insert(TaskEntity(name = "Calendar task"))
            }
            runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitText("Calendar")
                onNodeWithText("Calendar").performClick()
                waitText("Unscheduled")
                waitText("Calendar task")
                onNodeWithContentDescription("Next month").performClick()
                onNodeWithText(label).assertIsDisplayed()
                val source = onNodeWithTag("calendar-task-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                val target = onNodeWithTag("calendar-day-${next.firstDay}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                onRoot().performMouseInput { moveTo(source); press(); moveTo(source + Offset(28f, 0f)); moveTo(target); release() }
                waitText("Undo")
                assertEquals(next.firstDay.toEpochDays(), runBlocking { db.taskDao().getById(1) }?.dueDate)
                onNodeWithText("Undo").performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("No unscheduled tasks.")).fetchSemanticsNodes().isEmpty() }
                assertNull(runBlocking { db.taskDao().getById(1) }?.dueDate)
                onNodeWithText("Calendar task", useUnmergedTree = true).performClick()
                waitText("Edit task")
                onNodeWithContentDescription("Task name").performTextReplacement("Updated from calendar")
                onNodeWithContentDescription("Save task").performClick()
                waitText("Unscheduled")
                waitText("Updated from calendar")
                onNodeWithText(label).assertIsDisplayed()
                onNodeWithTag("calendar-day-${next.firstDay}", useUnmergedTree = true).performClick()
                waitText("Create task")
                onNodeWithContentDescription("Clear date").assertIsDisplayed()
                onNodeWithContentDescription("Task name").performTextReplacement("Created from calendar")
                onNodeWithContentDescription("Save task").performClick()
                waitText("Unscheduled")
                waitText("Created from calendar")
                assertEquals(next.firstDay.toEpochDays(), runBlocking { db.taskDao().getById(2) }?.dueDate)
                onNodeWithText(label).assertIsDisplayed()
                onNodeWithTag("calendar-day-number-${next.firstDay}", useUnmergedTree = true).performClick()
                waitText("Day view is coming soon.")
                onNodeWithText(next.firstDay.toString()).assertIsDisplayed()
                onNodeWithContentDescription("Back").performClick()
                waitText("Unscheduled")
                onNodeWithText(label).assertIsDisplayed()
                onNodeWithText("New task").performClick()
                waitText("Create task")
                onNodeWithContentDescription("Clear date").assertDoesNotExist()
                onNodeWithContentDescription("Back").performClick()
                waitText("Unscheduled")
                onNodeWithContentDescription("Back").performClick()
                waitText("Calendar")
                onNodeWithText("Tasks").assertIsDisplayed()
            }
            assertEquals("Updated from calendar", runBlocking { db.taskDao().getById(1) }?.name)
        }
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
