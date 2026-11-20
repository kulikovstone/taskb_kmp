package com.mkulikov.taskb

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.*
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
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

    @Test
    fun `compact calendar opens the day from a passive entry and carries the date to both editors`() {
        withAppKoin(file = directory.root.toPath().resolve("calendar-compact.db")) { app ->
            val month = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault()).yearMonth.plusMonth()
            val date = month.onDay(day = 12)
            val db = app.koin.get<AppDatabase>()
            runBlocking {
                app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false)
                db.taskDao().insert(
                    task = TaskEntity(
                        name = "Dated task",
                        description = "Details for the selected day",
                        dueDate = date.toEpochDays(),
                    ),
                )
                db.taskDao().insert(task = TaskEntity(name = "Undated task"))
            }
            runSkikoComposeUiTest(size = Size(width = 400f, height = 800f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitText(text = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                onNodeWithContentDescription(label = "Next month").performClick()
                waitText(text = "Dated task")
                onNodeWithText(text = "Dated task", useUnmergedTree = true)
                    .assert(matcher = !hasClickAction())
                    .performTouchInput { click() }
                waitText(text = "Details for the selected day")
                onNodeWithTag(testTag = "calendar-selected-day").assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-grid").assertDoesNotExist()

                onNodeWithContentDescription(label = "New task").performClick()
                waitText(text = "New task")
                onNodeWithContentDescription(label = "Task name").performTextInput(text = "New dated task")
                onNodeWithContentDescription(label = "Save task").performClick()
                waitText(text = "New dated task")
                assertEquals(
                    expected = date.toEpochDays(),
                    actual = runBlocking { db.taskDao().getById(id = 3) }?.dueDate,
                )

                onNodeWithContentDescription(label = "New event").performClick()
                waitText(text = "New event")
                onNodeWithContentDescription(label = "Event name").performTextInput(text = "New dated event")
                onNodeWithContentDescription(label = "Save event").performClick()
                waitText(text = "New dated event")
                assertEquals(
                    expected = date,
                    actual = runBlocking { app.koin.get<IEventRepository>().events.first().single().dueDate },
                )

                onNodeWithText(text = "No date").performClick()
                waitText(text = "Undated task")
                onNodeWithText(text = "No date").assertIsDisplayed()
                onNodeWithText(text = "Dated task").assertDoesNotExist()
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "Details for the selected day")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = monthLabel.format(value = month))
                onNodeWithTag(testTag = "calendar-day-$date").assertIsSelected()
            }
        }
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
                waitText(text = "Task")
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
    fun `calendar remembers explicitly chosen groups for the app session including cancelled drafts and clearing`() {
        val file = directory.root.toPath().resolve("calendar-create-group.db")
        withAppKoin(file = file) { app ->
            val db = app.koin.get<AppDatabase>()
            val groupIds = runBlocking {
                app.koin.get<IAppSettings>().setShowWelcomeScreen(enabled = false)
                listOf("Work", "Personal").associateWith { name ->
                    db.groupDao()
                        .insert(group = GroupEntity(name = name, color = Palette.Blue.s400.toArgb()))
                        .toInt()
                }
            }
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 1000f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitText(text = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                waitText(text = "Task")
                openNewTask(group = "No group")
                chooseTaskGroup(group = "Work")
                saveNewTask(name = "First in Work")
                assertEquals(
                    expected = groupIds.getValue(key = "Work"),
                    actual = runBlocking { db.taskDao().getById(id = 1) }?.groupId,
                )

                openNewTask(group = "Work")
                chooseTaskGroup(group = "Personal")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "First in Work")
                onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
                onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("No date")
                openNewTask(group = "Personal")
                saveNewTask(name = "Second in Personal")
                assertEquals(
                    expected = groupIds.getValue(key = "Personal"),
                    actual = runBlocking { db.taskDao().getById(id = 2) }?.groupId,
                )

                onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
                waitText(text = "First in Work")
                onNode(
                        matcher = hasText(text = "First in Work") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .performClick()
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Clear task group").performClick()
                onNodeWithContentDescription(label = "Save task").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(
                            matcher = hasText(text = "First in Work") and
                                hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                            useUnmergedTree = true,
                        )
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                openNewTask(group = "Personal")
                onNodeWithContentDescription(label = "Clear task group").performClick()
                saveNewTask(name = "No group task")
                assertNull(actual = runBlocking { db.taskDao().getById(id = 3) }?.groupId)

                openNewTask(group = "No group")
                chooseTaskGroup(group = "Work")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "First in Work")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "Tasks")
                onNodeWithText(text = "Tasks").performClick()
                waitText(text = "3 tasks")
                onNodeWithContentDescription(label = "Choose group").performClick()
                val personal = hasText(text = "Personal") and
                    SemanticsMatcher.keyIsDefined(key = SemanticsProperties.Selected)
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = personal).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(matcher = personal).performClick()
                waitText(text = "1 of 3 tasks")
                openNewTask(group = "Personal", label = "New task")
                onNodeWithContentDescription(label = "Clear task group").performClick()
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "1 of 3 tasks")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                waitText(text = "Task")
                openNewTask(group = "Work")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "First in Work")

                runBlocking { db.groupDao().deleteById(id = groupIds.getValue(key = "Work")) }
                openNewTask(group = "No group")
                chooseTaskGroup(group = "Personal")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "First in Work")
            }
        }
        withAppKoin(file = file) { app ->
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 1000f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitText(text = "Calendar")
                onNodeWithText(text = "Calendar").performClick()
                waitText(text = "Task")
                openNewTask(group = "No group")
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
                onNodeWithText(text = "Create task").assertDoesNotExist()
                onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
                waitText(text = "Calendar task")
                onNodeWithContentDescription(label = "Next month").performClick()
                onNodeWithText(text = label).assertIsDisplayed()
                val source = onNodeWithTag(testTag = "calendar-task-1", useUnmergedTree = true)
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
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Task name")
                    .performTextReplacement(text = "Updated from calendar")
                onNodeWithContentDescription(label = "Save task").performClick()
                waitText(text = "Updated from calendar")
                onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("No date")
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
                waitText(text = "Edit task")
                onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Updated day task")
                onNodeWithContentDescription(label = "Save task").performClick()
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
                onNodeWithText(text = "Task").performClick()
                waitText(text = "Create task")
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
                onNodeWithContentDescription(label = "Save task").performClick()
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
                onNodeWithContentDescription(label = "Save task").assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = gridTask, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(matcher = gridTask, useUnmergedTree = true).assertIsDisplayed()
                sidebarTask.assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
                onNode(
                        matcher = hasText(text = "Created from calendar") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .assertDoesNotExist()
                onNodeWithText(text = "Task").performClick()
                waitText(text = "Create task")
                onNodeWithContentDescription(label = "Clear date").assertDoesNotExist()
                onNodeWithContentDescription(label = "Task name")
                    .performTextReplacement(text = "Created without date")
                onNodeWithContentDescription(label = "Save task").performClick()
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

    private suspend fun SkikoComposeUiTest.openNewTask(group: String, label: String = "Task") {
        onNodeWithText(text = label).performClick()
        val field = hasContentDescription(value = "Choose task group") and hasText(text = group)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher = field).fetchSemanticsNodes().isNotEmpty() }
        onNode(matcher = field).assertIsDisplayed()
    }

    private suspend fun SkikoComposeUiTest.chooseTaskGroup(group: String) {
        onNodeWithContentDescription(label = "Choose task group").performClick()
        val row = hasText(text = group) and SemanticsMatcher.keyIsDefined(key = SemanticsProperties.Selected)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher = row).fetchSemanticsNodes().isNotEmpty() }
        onNode(matcher = row).performClick()
        val field = hasContentDescription(value = "Choose task group") and hasText(text = group)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher = field).fetchSemanticsNodes().isNotEmpty() }
    }

    private suspend fun SkikoComposeUiTest.saveNewTask(name: String) {
        onNodeWithContentDescription(label = "Task name").performTextReplacement(text = name)
        onNodeWithContentDescription(label = "Save task").performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(
                    matcher = hasText(text = name) and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
