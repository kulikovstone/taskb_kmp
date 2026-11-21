package com.mkulikov.taskb

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.*
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.format.MonthNames
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class CalendarNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()
    private val monthLabel = YearMonth.Format {
        monthName(names = MonthNames.ENGLISH_FULL)
        chars(value = " ")
        year()
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun `calendar year arrows and control wheel keep the month and load its tasks`() {
        withAppKoin(file = directory.root.toPath().resolve("calendar-year-navigation.db")) { app ->
            val month = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault()).yearMonth
            val nextYear = month.plusYear()
            val target = nextYear.firstDay
            val db = app.koin.get<AppDatabase>()
            runBlocking {
                app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false)
                db.taskDao()
                    .insert(
                        task = TaskEntity(
                            name = "Anniversary",
                            description = "Anniversary details",
                            dueDate = target.toEpochDays(),
                        )
                    )
            }
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitText(text = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                waitUntilExactlyOneExists(matcher = hasTestTag(testTag = "calendar-grid"), timeoutMillis = 10_000)
                onNodeWithText(text = "Choose date").assertDoesNotExist()
                onNodeWithContentDescription(label = "Next year").performClick()
                waitText(text = monthLabel.format(value = nextYear))
                onNodeWithText(text = monthLabel.format(value = nextYear)).assertIsDisplayed()
                waitText(text = "Anniversary")
                onNodeWithTag(testTag = "calendar-day-$target", useUnmergedTree = true).performClick()
                waitText(text = "Anniversary details")

                onNodeWithContentDescription(label = "Previous year").performClick()
                waitText(text = monthLabel.format(value = month))
                val grid = onNodeWithTag(testTag = "calendar-grid").fetchSemanticsNode()
                runOnIdle {
                    (grid.root as PlatformRootForTest).sendPointerEvent(
                        eventType = PointerEventType.Scroll,
                        position = grid.boundsInRoot.center,
                        scrollDelta = Offset(x = 0f, y = -1f),
                        keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = true),
                    )
                }
                waitText(text = monthLabel.format(value = month.minusYear()))
                onNodeWithText(text = "Anniversary details").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `calendar keeps day selection and month when editing and uses the sidebar instead of navigation`() {
        withAppKoin(file = directory.root.toPath().resolve("calendar.db")) { app ->
            val today = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault())
            val next = today.yearMonth.plusMonth()
            val label = monthLabel.format(value = next)
            val db = app.koin.get<AppDatabase>()
            runBlocking {
                app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false)
                db.taskDao().insert(task = TaskEntity(name = "Calendar task", description = "Unscheduled details"))
                db.taskDao()
                    .insert(
                        task = TaskEntity(
                            name = "Day task",
                            description = "Selected day details",
                            dueDate = next.firstDay.toEpochDays(),
                        )
                    )
                db.taskDao()
                    .insert(
                        task = TaskEntity(
                            name = "Today's task",
                            description = "Today's details",
                            dueDate = today.toEpochDays(),
                        )
                    )
            }
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 1000f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitText(text = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                waitText(text = "Today's details")
                onNode(
                        matcher = hasText(text = "Today's task") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .assertIsDisplayed()
                onNodeWithText(text = "New item").assertDoesNotExist()
                showUndatedTasks()
                waitText(text = "Calendar task")
                onNodeWithContentDescription(label = "Next month").performClick()
                onNodeWithText(text = label).assertIsDisplayed()
                val source = onNodeWithTag(testTag = "calendar-list-task-1", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .center
                val target = onNodeWithTag(testTag = "calendar-day-${next.firstDay}", useUnmergedTree = true)
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
                waitText(text = "Undo")
                assertEquals(
                    expected = next.firstDay.toEpochDays(),
                    actual = runBlocking { db.taskDao().getById(id = 1) }?.dueDate,
                )
                onNodeWithText(text = "Undo").performClick()
                waitText(text = "Unscheduled details")
                assertNull(actual = runBlocking { db.taskDao().getById(id = 1) }?.dueDate)
                onNodeWithText(text = "Calendar task", useUnmergedTree = true).performClick()
                waitText(text = "Edit item")
                onNodeWithContentDescription(label = "Task name")
                    .performTextReplacement(text = "Updated from calendar")
                onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                waitText(text = "Updated from calendar")
                onNodeWithContentDescription(label = "Clear task filter").assertIsDisplayed()
                onNodeWithText(text = label).assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-day-number-${next.firstDay}", useUnmergedTree = true)
                    .performClick()
                waitText(text = "Selected day details")
                onNode(
                        matcher = hasText(text = "Day task") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .performClick()
                waitText(text = "Edit item")
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Updated day task")
                onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                waitText(text = "Selected day details")
                onNode(
                        matcher = hasText(text = "Updated day task") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-day-${next.firstDay}", useUnmergedTree = true).assertIsSelected()
                onNodeWithText(text = label).assertIsDisplayed()
                onNodeWithText(text = "Day view is coming soon.").assertDoesNotExist()
                onNodeWithContentDescription(label = "Add item").performClick()
                waitText(text = "New item")
                onNodeWithContentDescription(label = "Clear date").assertIsDisplayed()
                onNodeWithContentDescription(label = "Task name")
                    .performTextReplacement(text = "Created from calendar")
                val visibilityLabel = "Show in calendar"
                onNodeWithText(text = visibilityLabel).assertDoesNotExist()
                onNodeWithContentDescription(label = "Choose repeat pattern").performClick()
                waitText(text = visibilityLabel)
                onNodeWithText(text = "After").performClick()
                onNodeWithContentDescription(label = "Repeat occurrence count").performTextReplacement(text = "1")
                onNodeWithText(text = visibilityLabel).assertIsOn().performClick()
                onNodeWithText(text = visibilityLabel).assertIsOff()
                onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                waitText(text = "Selected day details")
                waitText(text = "Created from calendar")
                val newTask = hasText(text = "Created from calendar")
                val sidebarTask = onNode(
                    matcher = newTask and hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                val gridTask = newTask and hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-grid"))
                sidebarTask.assertIsDisplayed()
                onNode(matcher = gridTask, useUnmergedTree = true).assertDoesNotExist()
                assertEquals(
                    expected = next.firstDay.toEpochDays(),
                    actual = runBlocking { db.taskDao().getById(id = 4) }?.dueDate,
                )

                sidebarTask.performClick()
                waitText(text = visibilityLabel)
                onNodeWithText(text = visibilityLabel).assertIsOff().performClick()
                onNodeWithText(text = "Save changes").assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = gridTask, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(matcher = gridTask, useUnmergedTree = true).assertIsDisplayed()
                sidebarTask.assertIsDisplayed()
                showUndatedTasks()
                onNode(
                        matcher = hasText(text = "Created from calendar") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .assertDoesNotExist()
                onNodeWithContentDescription(label = "Add item").performClick()
                waitText(text = "New item")
                onNodeWithContentDescription(label = "Clear date").assertDoesNotExist()
                onNodeWithContentDescription(label = "Task name")
                    .performTextReplacement(text = "Created without date")
                onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                waitText(text = "Created without date")
                assertNull(actual = runBlocking { db.taskDao().getById(id = 5) }?.dueDate)
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "Calendar")
                onNodeWithText(text = "Tasks").assertIsDisplayed()
            }
            assertEquals(
                expected = "Updated from calendar",
                actual = runBlocking { db.taskDao().getById(id = 1) }?.name,
            )
        }
    }

    private fun SkikoComposeUiTest.showUndatedTasks() {
        onNodeWithContentDescription(label = "Filters").performClick()
        onNodeWithText(text = "No date").performClick()
        onNodeWithText(text = "Apply").performClick()
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
