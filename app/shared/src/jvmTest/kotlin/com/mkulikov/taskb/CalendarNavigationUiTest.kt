package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.format.MonthNames
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import kotlin.test.*
import kotlin.time.Clock
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@OptIn(ExperimentalTestApi::class)
class CalendarNavigationUiTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `shared date picker cancels without navigating and choosing a distant date loads its tasks`() {
        withAppKoin(directory.root.toPath().resolve("calendar-date-picker.db")) { app ->
            val target = LocalDate(2036, 6, 12)
            val db = app.koin.get<AppDatabase>()
            runBlocking {
                app.koin.get<IAppSettings>().completeWelcome()
                db.taskDao().insert(TaskEntity(name = "Anniversary", description = "Chosen date details", dueDate = target.toEpochDays()))
            }
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitText("Calendar")
                onNodeWithText("Calendar").performClick()
                waitText("Task")
                onNodeWithTag("calendar-sidebar-toggle").performClick()
                onNodeWithTag("calendar-sidebar-title").assertTextEquals("No date")
                onNodeWithTag("calendar-choose-date").assertIsEnabled().performClick()
                onNodeWithTag("date-picker-panel").assertIsDisplayed()
                onNode(isDialog()).screenshot("calendar-date-picker")
                onNodeWithText("Cancel").performClick()
                onNodeWithTag("date-picker-panel").assertDoesNotExist()
                onNodeWithTag("calendar-sidebar-title").assertTextEquals("No date")

                onNodeWithTag("calendar-choose-date").performClick()
                onNodeWithContentDescription("Choose year").performClick()
                onNodeWithContentDescription("Year").performTextReplacement("2036")
                onNodeWithContentDescription("Year").performImeAction()
                onNodeWithContentDescription("Choose month").performClick()
                onNodeWithContentDescription("Choose June 2036").performClick()
                onNodeWithContentDescription("Choose June 12, 2036").performClick()
                waitText("Chosen date details")
                onNodeWithTag("date-picker-panel").assertDoesNotExist()
                onNodeWithText("June 2036").assertIsDisplayed()
                onNodeWithTag("calendar-sidebar-title").assertTextEquals("Jun 12, 2036")
                onNodeWithTag("calendar-day-$target", useUnmergedTree = true).assertIsSelected()
                onNode(hasText("Anniversary") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true)
                    .assertIsDisplayed()
                onRoot().screenshot("calendar-chosen-date")

                onNodeWithTag("calendar-choose-date").performClick()
                onNodeWithContentDescription("Choose June 12, 2036").assertIsSelected()
                onNodeWithText("Cancel").performClick()
                onNodeWithText("June 2036").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `calendar remembers explicitly chosen groups for the app session including cancelled drafts and clearing`() {
        val file = directory.root.toPath().resolve("calendar-create-group.db")
        withAppKoin(file) { app ->
            val db = app.koin.get<AppDatabase>()
            val groupIds = runBlocking {
                app.koin.get<IAppSettings>().completeWelcome()
                listOf("Work", "Personal").associateWith { name ->
                    db.groupDao().insert(GroupEntity(name = name, color = Palette.Blue.s400.toArgb())).toInt()
                }
            }
            runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitText("Calendar")
                onNodeWithText("Calendar").performClick()
                waitText("Task")
                openNewTask("No group")
                chooseTaskGroup("Work")
                saveNewTask("First in Work")
                assertEquals(groupIds.getValue("Work"), runBlocking { db.taskDao().getById(1) }?.groupId)

                openNewTask("Work")
                chooseTaskGroup("Personal")
                onNodeWithContentDescription("Back").performClick()
                waitText("First in Work")
                onNodeWithTag("calendar-sidebar-toggle").performClick()
                onNodeWithTag("calendar-sidebar-title").assertTextEquals("No date")
                openNewTask("Personal")
                saveNewTask("Second in Personal")
                assertEquals(groupIds.getValue("Personal"), runBlocking { db.taskDao().getById(2) }?.groupId)

                onNodeWithTag("calendar-sidebar-toggle").performClick()
                waitText("First in Work")
                onNode(hasText("First in Work") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).performClick()
                waitText("Edit task")
                onNodeWithContentDescription("Clear task group").performClick()
                onNodeWithContentDescription("Save task").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("First in Work") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
                        useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }
                openNewTask("Personal")
                onNodeWithContentDescription("Clear task group").performClick()
                saveNewTask("No group task")
                assertNull(runBlocking { db.taskDao().getById(3) }?.groupId)

                openNewTask("No group")
                chooseTaskGroup("Work")
                onNodeWithContentDescription("Back").performClick()
                waitText("First in Work")
                onNodeWithContentDescription("Back").performClick()
                waitText("Tasks")
                onNodeWithText("Tasks").performClick()
                waitText("3 tasks")
                onNodeWithContentDescription("Choose group").performClick()
                val personal = hasText("Personal") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
                waitUntil(timeoutMillis = 10_000) { onAllNodes(personal).fetchSemanticsNodes().isNotEmpty() }
                onNode(personal).performClick()
                waitText("1 of 3 tasks")
                openNewTask("Personal", "New task")
                onNodeWithContentDescription("Clear task group").performClick()
                onNodeWithContentDescription("Back").performClick()
                waitText("1 of 3 tasks")
                onNodeWithContentDescription("Back").performClick()
                waitText("Calendar")
                onNodeWithText("Calendar").performClick()
                waitText("Task")
                openNewTask("Work")
                onNodeWithContentDescription("Back").performClick()
                waitText("First in Work")

                runBlocking { db.groupDao().deleteById(groupIds.getValue("Work")) }
                openNewTask("No group")
                chooseTaskGroup("Personal")
                onNodeWithContentDescription("Back").performClick()
                waitText("First in Work")
            }
        }
        withAppKoin(file) { app ->
            runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitText("Calendar")
                onNodeWithText("Calendar").performClick()
                waitText("Task")
                openNewTask("No group")
            }
        }
    }

    @Test
    fun `calendar keeps day selection and month when editing and uses the sidebar instead of navigation`() {
        withAppKoin(directory.root.toPath().resolve("calendar.db")) { app ->
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val next = today.yearMonth.plusMonth()
            val label = YearMonth.Format { monthName(MonthNames.ENGLISH_FULL); chars(" "); year() }.format(next)
            val db = app.koin.get<AppDatabase>()
            runBlocking {
                app.koin.get<IAppSettings>().completeWelcome()
                db.taskDao().insert(TaskEntity(name = "Calendar task", description = "Unscheduled details"))
                db.taskDao().insert(TaskEntity(name = "Day task", description = "Selected day details", dueDate = next.firstDay.toEpochDays()))
                db.taskDao().insert(TaskEntity(name = "Today's task", description = "Today's details", dueDate = today.toEpochDays()))
            }
            runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitText("Calendar")
                onNodeWithText("Calendar").performClick()
                waitText("Today's details")
                onNode(hasText("Today's task") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).assertIsDisplayed()
                onNodeWithTag("calendar-choose-date").assertIsEnabled()
                onNodeWithText("Create task").assertDoesNotExist()
                onNodeWithTag("calendar-sidebar-toggle").performClick()
                waitText("Calendar task")
                onNodeWithContentDescription("Next month").performClick()
                onNodeWithText(label).assertIsDisplayed()
                val source = onNodeWithTag("calendar-task-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                val target = onNodeWithTag("calendar-day-${next.firstDay}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                onRoot().performMouseInput { moveTo(source); press(); moveTo(source + Offset(28f, 0f)); moveTo(target); release() }
                waitText("Undo")
                assertEquals(next.firstDay.toEpochDays(), runBlocking { db.taskDao().getById(1) }?.dueDate)
                onNodeWithText("Undo").performClick()
                waitText("Unscheduled details")
                assertNull(runBlocking { db.taskDao().getById(1) }?.dueDate)
                onNodeWithText("Calendar task", useUnmergedTree = true).performClick()
                waitText("Edit task")
                onNodeWithContentDescription("Task name").performTextReplacement("Updated from calendar")
                onNodeWithContentDescription("Save task").performClick()
                waitText("Updated from calendar")
                onNodeWithTag("calendar-sidebar-title").assertTextEquals("No date")
                onNodeWithText(label).assertIsDisplayed()
                onNodeWithTag("calendar-day-number-${next.firstDay}", useUnmergedTree = true).performClick()
                waitText("Selected day details")
                onNode(hasText("Day task") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).performClick()
                waitText("Edit task")
                onNodeWithContentDescription("Task name").performTextReplacement("Updated day task")
                onNodeWithContentDescription("Save task").performClick()
                waitText("Selected day details")
                onNode(hasText("Updated day task") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).assertIsDisplayed()
                onNodeWithTag("calendar-day-${next.firstDay}", useUnmergedTree = true).assertIsSelected()
                onNodeWithText(label).assertIsDisplayed()
                onNodeWithText("Day view is coming soon.").assertDoesNotExist()
                onNodeWithText("Task").performClick()
                waitText("Create task")
                onNodeWithContentDescription("Clear date").assertIsDisplayed()
                onNodeWithContentDescription("Task name").performTextReplacement("Created from calendar")
                onNodeWithContentDescription("Save task").performClick()
                waitText("Selected day details")
                waitText("Created from calendar")
                onNode(hasText("Created from calendar") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).assertIsDisplayed()
                assertEquals(next.firstDay.toEpochDays(), runBlocking { db.taskDao().getById(4) }?.dueDate)
                onNodeWithTag("calendar-sidebar-toggle").performClick()
                onNode(hasText("Created from calendar") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).assertDoesNotExist()
                onNodeWithText("Task").performClick()
                waitText("Create task")
                onNodeWithContentDescription("Clear date").assertDoesNotExist()
                onNodeWithContentDescription("Task name").performTextReplacement("Created without date")
                onNodeWithContentDescription("Save task").performClick()
                waitText("Created without date")
                assertNull(runBlocking { db.taskDao().getById(5) }?.dueDate)
                onNodeWithContentDescription("Back").performClick()
                waitText("Calendar")
                onNodeWithText("Tasks").assertIsDisplayed()
            }
            assertEquals("Updated from calendar", runBlocking { db.taskDao().getById(1) }?.name)
        }
    }

    private suspend fun SkikoComposeUiTest.openNewTask(group: String, label: String = "Task") {
        onNodeWithText(label).performClick()
        val field = hasContentDescription("Choose task group") and hasText(group)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(field).fetchSemanticsNodes().isNotEmpty() }
        onNode(field).assertIsDisplayed()
    }

    private suspend fun SkikoComposeUiTest.chooseTaskGroup(group: String) {
        onNodeWithContentDescription("Choose task group").performClick()
        val row = hasText(group) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        onNode(row).performClick()
        val field = hasContentDescription("Choose task group") and hasText(group)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(field).fetchSemanticsNodes().isNotEmpty() }
    }

    private suspend fun SkikoComposeUiTest.saveNewTask(name: String) {
        onNodeWithContentDescription("Task name").performTextReplacement(name)
        onNodeWithContentDescription("Save task").performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(name) and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
                useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun SemanticsNodeInteraction.screenshot(name: String) {
        val path = Path.of("build/reports/screenshots/$name.png")
        Files.createDirectories(path.parent)
        ImageIO.write(captureToImage().toAwtImage(), "png", path.toFile())
    }
}
