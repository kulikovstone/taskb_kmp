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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.feature.event.api.database.EventEntity
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication

@OptIn(ExperimentalTestApi::class)
class TaskGroupsNavigationUiTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val directory: Path
        get() = temporaryFolder.root.toPath()

    @Test
    fun `home opens new compact screens with persisted CRUD and confirmation dialogs`() {
        val file = directory.resolve("task-groups.db")
        withAppKoin(file = file) { application ->
            runBlocking { application.koin.get<IAppSettings>().completeWelcome() }
            runSkikoComposeUiTest(size = Size(width = 400f, height = 980f)) {
                setApp(application = application, dark = false)
                openGroups()
                waitText(text = "No groups yet.")
                onNodeWithContentDescription(label = "Search groups").assertIsDisplayed()
                onNodeWithContentDescription(label = "Add group").performClick()
                onNodeWithContentDescription(label = "Save group").assertIsNotEnabled()
                assertCompactEditorActions(isNew = true)
                onNodeWithText(text = "Planned").assertDoesNotExist()
                onNodeWithContentDescription(label = "Group name").assertIsFocused()
                onNodeWithContentDescription(label = "Clear group name").assertDoesNotExist()
                val initialNameBounds = onNodeWithContentDescription(
                    label = "Group name",
                ).fetchSemanticsNode().boundsInRoot
                onNodeWithContentDescription(label = "Group name").performTextInput(text = "Draft")
                val editingNameBounds = onNodeWithContentDescription(
                    label = "Group name",
                ).fetchSemanticsNode().boundsInRoot
                val clearNameBounds = onNodeWithContentDescription(
                    label = "Clear group name",
                ).fetchSemanticsNode().boundsInRoot
                assertTrue(actual = editingNameBounds.right < clearNameBounds.left)
                assertEquals(
                    expected = initialNameBounds.left,
                    actual = editingNameBounds.left,
                    absoluteTolerance = 0.5f,
                )
                assertEquals(
                    expected = initialNameBounds.right,
                    actual = clearNameBounds.right,
                    absoluteTolerance = 0.5f,
                )
                screenshot(name = "task-groups-compact-field-focused-light")
                onNodeWithContentDescription(label = "Clear group name").performClick()
                waitText(text = "Enter group name")
                onNodeWithContentDescription(label = "Group name").assertIsFocused()
                onNodeWithContentDescription(label = "Clear group name").assertDoesNotExist()
                assertEquals(
                    expected = initialNameBounds.width,
                    actual = onNodeWithContentDescription(label = "Group name").fetchSemanticsNode().boundsInRoot.width,
                    absoluteTolerance = 0.5f,
                )
                onNodeWithContentDescription(label = "Save group").assertIsNotEnabled()
                onNodeWithContentDescription(label = "Group name").performTextInput(text = " Travel ")
                onNodeWithContentDescription(label = "Orange").performClick()
                onNodeWithContentDescription(label = "Orange").assertIsSelected()
                onNodeWithContentDescription(label = "Clear group name").assertDoesNotExist()
                assertEquals(
                    expected = initialNameBounds.width,
                    actual = onNodeWithContentDescription(label = "Group name").fetchSemanticsNode().boundsInRoot.width,
                    absoluteTolerance = 0.5f,
                )
                screenshot(name = "task-groups-compact-new-light")
                saveGroup()
                waitText(text = "Travel")
                onNodeWithText(text = "Travel").performClick()
                waitText(text = "Planned")
                onNodeWithContentDescription(label = "Save group").assertIsNotEnabled()
                assertCompactEditorActions(isNew = false)
                onNodeWithContentDescription(label = "Group name").performTextReplacement(text = "Work")
                onNodeWithContentDescription(label = "Blue").performClick()
                screenshot(name = "task-groups-compact-edit-light")
                saveGroup()
                waitText(text = "Work")
                onNodeWithContentDescription(label = "Add group").performClick()
                onNodeWithContentDescription(label = "Group name").performTextInput(text = "work")
                saveGroup()
                waitText(text = "Name conflict")
                onNodeWithText(text = "OK").performClick()
                onNodeWithContentDescription(label = "Group name").assertTextEquals("work")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "Work")
                onNodeWithText(text = "Work").performClick()
                waitText(text = "Planned")
                onNodeWithText(text = "Delete with tasks").assertDoesNotExist()
                onNodeWithContentDescription(label = "Delete group").performClick()
                waitText(text = "Delete group")
                onNodeWithText(text = "Cancel").performClick()
                onNodeWithContentDescription(label = "Delete group").performClick()
                waitText(text = "Delete group")
                onNode(matcher = hasText(text = "Delete") and hasAnyAncestor(matcher = isDialog())).performClick()
                waitText(text = "No groups yet.")
            }
            assertEquals(
                expected = emptyList(),
                actual = runBlocking { application.koin.get<AppDatabase>().groupDao().observeGroups().first() },
            )
        }
    }

    @Test
    fun `separate editor preserves its draft on resize and returns to list filters`() {
        withAppKoin(file = directory.resolve("resize.db")) { application ->
            seedGroups(application = application)
            val width = mutableStateOf(value = 400.dp)
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 980f)) {
                setApp(application = application, dark = false, contentWidth = width)
                openGroups()
                waitText(text = "8 groups")
                screenshot(name = "task-groups-compact-list-light")
                onNodeWithContentDescription(label = "Search groups").performTextInput(text = "wor")
                waitText(text = "1 of 8 groups")
                onNodeWithText(text = "Work").performClick()
                waitText(text = "Planned")
                onNodeWithContentDescription(label = "Group name").performTextReplacement(text = "Work draft")
                onNodeWithContentDescription(label = "Green").performClick()
                runOnIdle { width.value = 300.dp }
                waitForIdle()
                assertCompactEditorActions(isNew = false)
                runOnIdle { width.value = 1280.dp }
                waitText(text = "Save changes")
                onNodeWithContentDescription(label = "Search groups").assertDoesNotExist()
                onNodeWithText(text = "Groups").assertDoesNotExist()
                onNodeWithContentDescription(label = "Group name").assertTextEquals("Work draft")
                onNodeWithContentDescription(label = "Green").assertIsSelected()
                val form = onNode(
                        matcher = hasScrollAction() and
                            hasAnyDescendant(matcher = hasContentDescription(value = "Group name"))
                    )
                    .fetchSemanticsNode()
                    .boundsInRoot
                assertEquals(expected = 640f, actual = form.center.x, absoluteTolerance = 0.5f)
                assertEquals(expected = 720f, actual = form.width, absoluteTolerance = 0.5f)
                assertTrue(
                    actual =
                        onNodeWithText(text = "Planned").fetchSemanticsNode().boundsInRoot.top <
                            onNodeWithContentDescription(label = "Group name").fetchSemanticsNode().boundsInRoot.top
                )
                screenshot(name = "task-groups-wide-edit-light")
                runOnIdle { width.value = 839.dp }
                waitForIdle()
                assertCompactEditorActions(isNew = false)
                onNodeWithContentDescription(label = "Group name").assertTextEquals("Work draft")
                runOnIdle { width.value = 840.dp }
                waitText(text = "Save changes")
                onNodeWithContentDescription(label = "Group name").assertTextEquals("Work draft")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "1 of 8 groups")
                onNodeWithContentDescription(label = "Search groups").assertTextEquals("wor").performClick()
                onNodeWithContentDescription(label = "Clear search groups").performClick()
                onNodeWithText(text = "Empty 8").performClick()
                waitText(text = "8 of 8 groups")
                onNodeWithText(text = "New group").performClick()
                waitText(text = "Create group")
                onNodeWithContentDescription(label = "Group name").performTextInput(text = "Travel draft")
                onNodeWithContentDescription(label = "Orange").performClick()
                runOnIdle { width.value = 320.dp }
                waitForIdle()
                onNodeWithContentDescription(label = "Group name").assertTextEquals("Travel draft")
                onNodeWithContentDescription(label = "Orange").assertIsSelected()
                assertCompactEditorActions(isNew = true)
                runOnIdle { width.value = 1280.dp }
                waitText(text = "Create group")
                screenshot(name = "task-groups-wide-new-light")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "8 of 8 groups")
                onNodeWithText(text = "New group").performClick()
                onNodeWithText(text = "Enter group name").assertIsDisplayed()
                onNodeWithContentDescription(label = "Group name").assertIsFocused()
            }
            val groups = runBlocking { application.koin.get<AppDatabase>().groupDao().observeGroups().first() }
            assertEquals(expected = 8, actual = groups.size)
            assertEquals(expected = "Work", actual = groups.last().name)
            assertEquals(expected = Palette.Blue.s400.toArgb(), actual = groups.last().color)
        }
    }

    @Test
    fun `dark desktop and compact editor render the approved palette and saved data survives reopening`() {
        val file = directory.resolve("dark.db")
        withAppKoin(file = file) { application ->
            seedGroups(application = application)
            val width = mutableStateOf(value = 1280.dp)
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 980f)) {
                setApp(application = application, dark = true, contentWidth = width)
                openGroups()
                waitText(text = "8 groups")
                onNodeWithText(text = "Work").performClick()
                waitText(text = "Planned")
                screenshot(name = "task-groups-wide-edit-dark")
                onNodeWithContentDescription(label = "Group name").performTextReplacement(text = "Work renamed")
                saveGroup()
                waitText(text = "Work renamed")
                onNodeWithContentDescription(label = "Search groups").performTextInput(text = "missing")
                waitText(text = "No groups match your search or filter.")
                onNodeWithContentDescription(label = "Clear search groups").performClick()
                waitText(text = "8 groups")
                onNodeWithText(text = "New group").performClick()
                onNodeWithContentDescription(label = "Group name").performTextInput(text = "Travel")
                screenshot(name = "task-groups-wide-new-dark")
                runOnIdle { width.value = 400.dp }
                waitForIdle()
                screenshot(name = "task-groups-compact-new-dark")
                onNodeWithContentDescription(label = "Back").performClick()
                waitText(text = "8 groups")
                screenshot(name = "task-groups-compact-list-dark")
                onNodeWithText(text = "Work renamed").performClick()
                waitText(text = "Planned")
                screenshot(name = "task-groups-compact-edit-dark")
            }
        }
        withAppKoin(file = file) { application ->
            assertEquals(
                expected = "Work renamed",
                actual = runBlocking { application.koin.get<AppDatabase>().groupDao().observeGroups().first() }
                    .last()
                    .name,
            )
        }
    }

    @Test
    fun `groups sort by live event counts without changing task totals`() {
        withAppKoin(file = directory.resolve("group-events.db")) { application ->
            val database = application.koin.get<AppDatabase>()
            val (alphaId, movedEvent) = runBlocking {
                application.koin.get<IAppSettings>().completeWelcome()
                val groups = database.groupDao()
                val alpha = groups.insert(group = GroupEntity(name = "Alpha", color = Palette.Blue.s400.toArgb()))
                    .toInt()
                val beta = groups.insert(group = GroupEntity(name = "Beta", color = Palette.Green.s400.toArgb()))
                    .toInt()
                groups.insert(group = GroupEntity(name = "Gamma", color = Palette.Orange.s400.toArgb()))
                repeat(times = 2) {
                    database.taskDao().insert(task = TaskEntity(name = "Task", groupId = alpha))
                }
                val events = database.eventDao()
                events.insert(event = EventEntity(name = "Event in Alpha", groupId = alpha))
                events.insert(event = EventEntity(name = "Event in Beta", groupId = beta))
                val event = EventEntity(name = "Moved event", groupId = beta)
                val eventId = events.insert(event = event).toInt()
                alpha to event.copy(id = eventId)
            }

            runSkikoComposeUiTest(size = Size(width = 400f, height = 720f)) {
                setApp(application = application, dark = false)
                openGroups()
                waitText(text = "3 groups")
                onNodeWithTag(testTag = "group-list-scrollbar").assertDoesNotExist()
                val events = onNodeWithContentDescription(label = "Sort by Events")

                fun groupOrder(): List<String> = listOf("Alpha", "Beta", "Gamma").sortedBy { name ->
                    onNodeWithText(text = name).fetchSemanticsNode().boundsInRoot.top
                }

                onNodeWithText(text = "Alpha").assertTextEquals("Alpha", "2", "1")

                events.performClick()
                waitUntil(timeoutMillis = 10_000) {
                    groupOrder() == listOf("Beta", "Alpha", "Gamma")
                }

                runBlocking { database.eventDao().update(event = movedEvent.copy(groupId = alphaId)) }
                waitUntil(timeoutMillis = 10_000) {
                    groupOrder() == listOf("Alpha", "Beta", "Gamma")
                }
                onNodeWithText(text = "Alpha").assertTextEquals("Alpha", "2", "2")
                onNodeWithText(text = "Gamma").assertTextEquals("Gamma", "0", "0")

                events.performClick()
                waitUntil(timeoutMillis = 10_000) {
                    groupOrder() == listOf("Gamma", "Beta", "Alpha")
                }
            }
        }
    }

    @Test
    fun `list and editor scroll areas reach the bottom of compact and wide windows`() {
        withAppKoin(file = directory.resolve("bottom-edge.db")) { application ->
            seedGroups(application = application)
            val width = mutableStateOf(value = 400.dp)
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 420f)) {
                setApp(application = application, dark = false, contentWidth = width)
                openGroups()
                waitText(text = "8 groups")
                for (availableWidth in listOf(400.dp, 1280.dp)) {
                    runOnIdle { width.value = availableWidth }
                    waitForIdle()
                    val bottom = onRoot().fetchSemanticsNode().boundsInRoot.bottom
                    val list = onNode(matcher = SemanticsMatcher.keyIsDefined(key = SemanticsActions.ScrollToIndex))
                    val scrollbar = onNodeWithTag(testTag = "group-list-scrollbar")
                    scrollbar.assertIsDisplayed()
                    assertEquals(
                        expected = bottom,
                        actual = list.fetchSemanticsNode().boundsInRoot.bottom,
                        absoluteTolerance = 0.5f,
                    )
                    list.performScrollToIndex(index = 7)
                    scrollbar.assertIsDisplayed()
                    onNodeWithText(text = "Work").assertIsDisplayed().performClick()
                    waitText(text = "Planned")
                    val form = onNode(
                        matcher = hasScrollAction() and
                            hasAnyDescendant(matcher = hasContentDescription(value = "Group name"))
                    )
                    assertEquals(
                        expected = bottom,
                        actual = form.fetchSemanticsNode().boundsInRoot.bottom,
                        absoluteTolerance = 0.5f,
                    )
                    onNodeWithText(text = "Delete with tasks").assertDoesNotExist()
                    onNodeWithText(text = "Actions").assertDoesNotExist()
                    if (availableWidth == 400.dp) {
                        onNodeWithContentDescription(label = "Blue grey").performScrollTo().assertIsDisplayed()
                        assertCompactEditorActions(isNew = false)
                    } else {
                        val delete = onNodeWithText(text = "Delete")
                        delete.assertIsDisplayed()
                        val deleteBounds = delete.fetchSemanticsNode().boundsInRoot
                        val saveBounds = onNodeWithContentDescription(
                            label = "Save group",
                        ).fetchSemanticsNode().boundsInRoot
                        assertEquals(
                            expected = saveBounds.center.y,
                            actual = deleteBounds.center.y,
                            absoluteTolerance = 0.5f,
                        )
                    }
                    screenshot(
                        name = if (availableWidth == 400.dp) {
                            "task-groups-compact-bottom-edge"
                        } else {
                            "task-groups-wide-bottom-edge"
                        }
                    )
                    onNodeWithContentDescription(label = "Back").performClick()
                    waitText(text = "8 groups")
                }
            }
        }
    }

    private fun seedGroups(application: KoinApplication) {
        runBlocking {
            application.koin.get<IAppSettings>().completeWelcome()
            val dao = application.koin.get<AppDatabase>().groupDao()
            listOf(
                    "Health" to Palette.Teal.s400,
                    "Ideas" to Palette.Amber.s400,
                    "Learning" to Palette.Purple.s400,
                    "Personal" to Palette.Green.s400,
                    "Reading" to Palette.Indigo.s400,
                    "Shopping" to Palette.Orange.s400,
                    "Someday" to Palette.Grey.s400,
                    "Work" to Palette.Blue.s400,
                )
                .forEach { (name, color) ->
                    dao.insert(group = GroupEntity(name = name, color = color.toArgb()))
                }
        }
    }

    private fun SkikoComposeUiTest.setApp(
        application: KoinApplication,
        dark: Boolean,
        contentWidth: State<Dp>? = null,
    ) {
        setContent {
            KoinIsolatedContext(context = application) {
                TaskBTheme(darkTheme = dark) {
                    Box(modifier = Modifier.width(width = contentWidth?.value ?: 400.dp).fillMaxHeight()) {
                        TaskBApp(settings = application.koin.get())
                    }
                }
            }
        }
    }

    private suspend fun SkikoComposeUiTest.openGroups() {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = "Groups") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        onNode(matcher = hasText(text = "Groups") and hasClickAction()).performClick()
        waitText(text = "Groups")
    }

    private suspend fun SkikoComposeUiTest.saveGroup() {
        waitForIdle()
        onNodeWithContentDescription(label = "Save group").assertIsDisplayed().performClick()
    }

    private fun SkikoComposeUiTest.assertCompactEditorActions(isNew: Boolean) {
        val backBounds = onNodeWithContentDescription(label = "Back").fetchSemanticsNode().boundsInRoot
        val saveBounds = onNodeWithContentDescription(
            label = "Save group",
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(expected = backBounds.center.y, actual = saveBounds.center.y, absoluteTolerance = 0.5f)
        assertTrue(actual = saveBounds.left >= backBounds.right)
        onNode(
                matcher = hasContentDescription(value = "Save group") and hasAnyAncestor(matcher = hasScrollAction())
            )
            .assertDoesNotExist()
        onNodeWithText(text = "Create group").assertDoesNotExist()
        onNodeWithText(text = "Save changes").assertDoesNotExist()
        onNodeWithText(text = "Delete").assertDoesNotExist()
        if (isNew) {
            onNodeWithContentDescription(label = "Delete group").assertDoesNotExist()
        } else {
            val deleteBounds = onNodeWithContentDescription(label = "Delete group")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            assertEquals(expected = backBounds.center.y, actual = deleteBounds.center.y, absoluteTolerance = 0.5f)
            assertTrue(actual = deleteBounds.left >= backBounds.right)
            assertTrue(actual = deleteBounds.right <= saveBounds.left)
            onNode(
                    matcher = hasContentDescription(value = "Delete group") and
                        hasAnyAncestor(matcher = hasScrollAction())
                )
                .assertDoesNotExist()
        }
    }

    private suspend fun SkikoComposeUiTest.waitText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(matcher = hasText(text = text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val output = Path.of("build/reports/screenshots")
        Files.createDirectories(output)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.resolve("$name.png").toFile())
    }
}
