package com.mkulikov.taskb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication

@OptIn(ExperimentalTestApi::class)
class TaskListNavigationUiTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val directory: Path
        get() = temporaryFolder.root.toPath()

    @Test
    fun `compact CRUD date and group choices completion filters and deletion persist`() {
        val file = directory.resolve("crud.db")
        withAppKoin(file = file) { app ->
            seed(app = app, tasks = false)
            runSkikoComposeUiTest(size = Size(width = 400f, height = 980f)) {
                setApp(app = app)
                openTasks()
                waitText(text = "No tasks yet.")
                onNodeWithContentDescription(label = "Add task").performClick()
                onNodeWithContentDescription(label = "Save task").assertIsNotEnabled()
                screenshot(name = "task-compact-new-empty")
                onNodeWithContentDescription(label = "Task name")
                    .assertIsFocused()
                    .performTextInput(text = " New task ")
                onNodeWithText(text = "Tomorrow").performClick()
                onNodeWithText(text = "Tomorrow").assertIsSelected()
                onNodeWithContentDescription(label = "Choose task group").performClick()
                waitText(text = "Choose group")
                onNodeWithContentDescription(label = "Search groups").performTextInput(text = "wor")
                onNodeWithText(text = "Work").performClick()
                waitText(text = "New task")
                screenshot(name = "task-compact-new")
                onNodeWithContentDescription(label = "Save task").performClick()
                waitText(text = "1 task")
                onNodeWithText(text = "New task").performClick()
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Save task").assertIsNotEnabled()
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Edited task")
                onNodeWithContentDescription(label = "Choose date").performClick()
                waitText(text = "Choose date")
                onNodeWithContentDescription(label = "Next month").performClick()
                screenshot(name = "task-date-picker", dialog = true)
                val chosen = YearMonth.from(LocalDate.now().plusDays(1)).plusMonths(1).atDay(15)
                onNodeWithContentDescription(
                        label =
                            "Choose ${chosen.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))}"
                    )
                    .performClick()
                clearSelection(description = "Clear date")
                clearSelection(description = "Clear task group")
                screenshot(name = "task-compact-edit")
                onNodeWithContentDescription(label = "Save task").performClick()
                waitText(text = "1 task")
                onNodeWithContentDescription(label = "Complete Edited task").performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodes(matcher = hasContentDescription(value = "Mark Edited task incomplete"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Choose task filter").performClick()
                screenshot(name = "task-compact-filters")
                onNodeWithText(text = "Completed").performClick()
                waitText(text = "1 of 1 tasks")
                onNodeWithContentDescription(label = "Choose group").performClick()
                onNodeWithText(text = "No group").performClick()
                waitText(text = "1 of 1 tasks")
                onNodeWithContentDescription(label = "Choose group").performClick()
                onNodeWithText(text = "No group").assertIsSelected()
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "1 of 1 tasks")
                clearSelection(description = "Clear task filter")
                onNodeWithText(text = "Edited task").performClick()
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Delete task").performClick()
                waitText(text = "Delete task")
                onNodeWithText(text = "Cancel").performClick()
                onNodeWithContentDescription(label = "Delete task").performClick()
                onNode(matcher = hasText(text = "Delete") and hasAnyAncestor(matcher = isDialog())).performClick()
                waitText(text = "No tasks yet.")
            }
            assertTrue(
                actual = runBlocking { app.koin.get<AppDatabase>().taskDao().observeTasks().first() }.isEmpty()
            )
        }
        withAppKoin(file = file) { app ->
            assertTrue(
                actual = runBlocking { app.koin.get<AppDatabase>().taskDao().observeTasks().first() }.isEmpty()
            )
        }
    }

    @Test
    fun `wide layouts match task designs and resizing preserves draft and active selectors`() {
        withAppKoin(file = directory.resolve("resize.db")) { app ->
            seed(app = app)
            val width = mutableStateOf(value = 1280.dp)
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 980f)) {
                setApp(app = app, width = width)
                openTasks()
                waitText(text = "8 tasks")
                onNodeWithText(text = "Expired completion").assertDoesNotExist()
                screenshot(name = "task-wide-list")
                onNodeWithText(text = "New task").performClick()
                waitText(text = "Create task")
                onNodeWithContentDescription(label = "Save task").assertIsNotEnabled()
                screenshot(name = "task-wide-new-empty")
                onNodeWithContentDescription(label = "Task name").performTextInput(text = "Plan the next release")
                screenshot(name = "task-wide-new")
                runOnIdle { width.value = 300.dp }
                waitForIdle()
                onNodeWithContentDescription(label = "Task name").assertTextEquals("Plan the next release")
                runOnIdle { width.value = 1280.dp }
                waitText(text = "Create task")
                onNodeWithContentDescription(label = "Back").performClick()
                onNodeWithText(text = "Prepare task screen mockups").performClick()
                waitText(text = "Save changes")
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Task draft")
                screenshot(name = "task-wide-edit")
                onNodeWithContentDescription(label = "Choose task group").performClick()
                waitText(text = "Choose group")
                onNode(matcher = isDialog()).assertDoesNotExist()
                onNodeWithText(text = "No group").assertDoesNotExist()
                screenshot(name = "task-wide-groups")
                onNodeWithContentDescription(label = "Search groups").performTextInput(text = "hea")
                runOnIdle { width.value = 400.dp }
                waitForIdle()
                onNodeWithText(text = "Choose group").assertIsDisplayed()
                onNodeWithContentDescription(label = "Search groups").assertTextEquals("hea")
                screenshot(name = "task-compact-groups")
                onNodeWithText(text = "Health").performClick()
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Task name").assertTextEquals("Task draft")
                onNodeWithContentDescription(label = "Choose task group").assert(matcher = hasText(text = "Health"))
                runOnIdle { width.value = 300.dp }
                waitForIdle()
                screenshot(name = "task-narrow-edit")
                runOnIdle { width.value = 840.dp }
                waitText(text = "Save changes")
                onNodeWithContentDescription(label = "Task name").assertTextEquals("Task draft")
                runOnIdle { width.value = 1280.dp }
                waitForIdle()
                onNodeWithContentDescription(label = "Back").performClick()
                onNodeWithContentDescription(label = "Choose task filter").performClick()
                waitText(text = "Filters")
                screenshot(name = "task-wide-filters", dialog = true)
                onNode(matcher = hasText(text = "Today") and hasAnyAncestor(matcher = isDialog())).performClick()
                waitText(text = "2 of 8 tasks")
                onNodeWithContentDescription(label = "Choose group").performClick()
                onNodeWithText(text = "No group").assertIsDisplayed()
                onNodeWithText(text = "Health").performClick()
                waitText(text = "1 of 8 tasks")
                screenshot(name = "task-wide-filtered")
                onNodeWithText(text = "New task").performClick()
                waitText(text = "Create task")
                onNodeWithText(text = "Today").assertIsSelected()
                onNodeWithContentDescription(label = "Choose task group").assert(matcher = hasText(text = "Health"))
                onNodeWithContentDescription(label = "Search tasks").assertDoesNotExist()
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "1 of 8 tasks")
                runOnIdle { width.value = 400.dp }
                waitForIdle()
                screenshot(name = "task-compact-filtered")
                clearSelection(description = "Clear group filter")
                clearSelection(description = "Clear task filter")
                waitText(text = "8 tasks")
                onNodeWithText(text = "Expired completion").assertDoesNotExist()
                screenshot(name = "task-compact-list")
                onNodeWithContentDescription(label = "Search tasks").performTextInput(text = "nonexistent")
                waitText(text = "No tasks match your search or filters.")
                onNodeWithContentDescription(label = "Clear search tasks").performClick()
                onNodeWithContentDescription(label = "Search tasks").assertIsFocused()
            }
        }
    }

    @Test
    fun `dark short windows keep editor actions and long selections reachable`() {
        withAppKoin(file = directory.resolve("dark.db")) { app ->
            seed(app = app)
            val width = mutableStateOf(value = 1280.dp)
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 420f)) {
                setApp(app = app, dark = true, width = width)
                openTasks()
                waitText(text = "8 tasks")
                onNodeWithText(text = "Prepare task screen mockups").performClick()
                waitText(text = "Save changes")
                onNodeWithContentDescription(label = "Save task").assertIsDisplayed()
                screenshot(name = "task-wide-short-dark")
                onNodeWithContentDescription(label = "Choose task group").performScrollTo().performClick()
                waitText(text = "Choose group")
                waitText(text = "Work")
                onNodeWithText(text = "Work").assertIsDisplayed().assert(matcher = hasText(text = "1"))
                onNode(
                        matcher = SemanticsMatcher.keyIsDefined(
                            key = androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex
                        )
                    )
                    .performScrollToIndex(index = 7)
                onNodeWithText(text = "Someday").assertIsDisplayed()
                onNodeWithContentDescription(label = "Back").performClick()
                runOnIdle { width.value = 300.dp }
                waitForIdle()
                onNodeWithContentDescription(label = "Save task").assertIsDisplayed()
                onNodeWithContentDescription(label = "Choose task group").performScrollTo().assertIsDisplayed()
                screenshot(name = "task-narrow-short-dark")
            }
        }
    }

    private fun seed(app: KoinApplication, tasks: Boolean = true) {
        runBlocking {
            app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false)
            val db = app.koin.get<AppDatabase>()
            val groupIds = listOf(
                    "Health" to Palette.Teal.s400,
                    "Ideas" to Palette.Amber.s400,
                    "Learning" to Palette.Purple.s400,
                    "Personal" to Palette.Green.s400,
                    "Reading" to Palette.Indigo.s400,
                    "Shopping" to Palette.Orange.s400,
                    "Someday" to Palette.Grey.s400,
                    "Work" to Palette.Blue.s400,
                )
                .associate { (name, color) ->
                    name to db.groupDao().insert(group = GroupEntity(name = name, color = color.toArgb())).toInt()
                }
            if (tasks) {
                val today = LocalDate.now()
                val now = Clock.System.now()
                listOf(
                        TaskEntity(
                            name = "Prepare task screen mockups",
                            dueDate = today.minusDays(1).toEpochDay(),
                            groupId = groupIds["Work"],
                        ),
                        TaskEntity(
                            name = "Book a dentist appointment",
                            dueDate = today.toEpochDay(),
                            groupId = groupIds["Health"],
                        ),
                        TaskEntity(
                            name = "Buy groceries for the weekend",
                            dueDate = today.toEpochDay(),
                            groupId = groupIds["Shopping"],
                        ),
                        TaskEntity(
                            name = "Read a chapter",
                            dueDate = today.plusDays(1).toEpochDay(),
                            groupId = groupIds["Reading"],
                        ),
                        TaskEntity(
                            name = "Practice Kotlin coroutines",
                            dueDate = today.plusDays(2).toEpochDay(),
                            groupId = groupIds["Learning"],
                        ),
                        TaskEntity(name = "Plan the next week", groupId = groupIds["Personal"]),
                        TaskEntity(
                            name = "Send the project update",
                            groupId = groupIds["Work"],
                            completedAt = now.toEpochMilliseconds(),
                        ),
                        TaskEntity(
                            name = "Go for an evening walk",
                            groupId = groupIds["Health"],
                            completedAt = now.toEpochMilliseconds(),
                        ),
                        TaskEntity(name = "Expired completion", completedAt = (now - 25.hours).toEpochMilliseconds()),
                    )
                    .forEach { task -> db.taskDao().insert(task = task) }
            }
        }
    }

    private fun SkikoComposeUiTest.setApp(app: KoinApplication, dark: Boolean = false, width: State<Dp>? = null) {
        setContent {
            KoinIsolatedContext(context = app) {
                TaskBTheme(darkTheme = dark) {
                    Box(modifier = Modifier.width(width = width?.value ?: 400.dp).fillMaxHeight()) {
                        TaskBApp(settings = app.koin.get())
                    }
                }
            }
        }
    }

    private suspend fun SkikoComposeUiTest.openTasks() {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = "Tasks") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        onNode(matcher = hasText(text = "Tasks") and hasClickAction()).performClick()
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private suspend fun SkikoComposeUiTest.clearSelection(description: String) {
        onNodeWithContentDescription(label = description).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(matcher = hasContentDescription(value = description)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun SkikoComposeUiTest.screenshot(name: String, dialog: Boolean = false) {
        val output = Path.of("build/reports/screenshots")
        Files.createDirectories(output)
        ImageIO.write(
            (if (dialog) onNode(matcher = isDialog()) else onRoot()).captureToImage().toAwtImage(),
            "png",
            output.resolve("$name.png").toFile(),
        )
    }
}
