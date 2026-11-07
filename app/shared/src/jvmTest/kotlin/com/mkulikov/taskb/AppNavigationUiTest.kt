package com.mkulikov.taskb

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class AppNavigationUiTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val temporaryDirectory: Path
        get() = temporaryFolder.root.toPath()

    @Test
    fun `continue opens home and persists completed welcome`() {
        val file = temporaryDirectory.resolve("taskb.db")
        withAppKoin(file = file) { application ->
            val settings = application.koin.get<IAppSettings>()
            runComposeUiTest {
                setContent {
                    KoinIsolatedContext(context = application) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = settings) }
                    }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasText(text = "Continue")).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText(text = "Welcome to TaskB").assertIsDisplayed()
                onNodeWithText(text = "Continue").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasText(text = "Tasks") and hasClickAction())
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNode(matcher = hasText(text = "Tasks") and hasClickAction()).assertIsDisplayed()
                onNode(matcher = hasText(text = "Groups") and hasClickAction()).assertIsDisplayed()
                onNodeWithText(text = "Home screen").assertDoesNotExist()
            }
        }
        withAppKoin(file = file) { application ->
            assertTrue(actual = runBlocking { application.koin.get<IAppSettings>().hasCompletedWelcome() })
        }
    }

    @Test
    fun `returning user starts with home and can open tasks and groups and return`() {
        withAppKoin(file = temporaryDirectory.resolve("taskb.db")) { application ->
            val settings = application.koin.get<IAppSettings>()
            runBlocking { settings.completeWelcome() }
            runComposeUiTest {
                setContent {
                    KoinIsolatedContext(context = application) {
                        TaskBTheme(darkTheme = true) { TaskBApp(settings = settings) }
                    }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasText(text = "Tasks") and hasClickAction())
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNode(matcher = hasText(text = "Tasks") and hasClickAction()).assertIsDisplayed()
                onNode(matcher = hasText(text = "Groups") and hasClickAction()).assertIsDisplayed()
                onNodeWithText(text = "Home screen").assertDoesNotExist()
                onNodeWithText(text = "Welcome to TaskB").assertDoesNotExist()
                val version = onNodeWithText(text = "Version ${AppVersion.value}").assertIsDisplayed()
                val screenBounds = onRoot().fetchSemanticsNode().boundsInRoot
                val versionBounds = version.fetchSemanticsNode().boundsInRoot
                assertTrue(actual = versionBounds.center.y > screenBounds.top + screenBounds.height * 0.8f)

                onNode(matcher = hasText(text = "Tasks") and hasClickAction()).performClick()
                onNodeWithContentDescription(label = "Back").assertIsDisplayed()
                onNodeWithText(text = "Tasks").assertIsDisplayed()
                onNode(matcher = hasText(text = "Groups") and hasClickAction()).assertDoesNotExist()
                onNodeWithContentDescription(label = "Back").performClick()
                onNode(matcher = hasText(text = "Tasks") and hasClickAction()).assertIsDisplayed()
                onNode(matcher = hasText(text = "Groups") and hasClickAction()).assertIsDisplayed()
                onNodeWithText(text = "Version ${AppVersion.value}").assertIsDisplayed()

                onNode(matcher = hasText(text = "Groups") and hasClickAction()).performClick()
                onNodeWithContentDescription(label = "Back").assertIsDisplayed()
                onNodeWithText(text = "Groups").assertIsDisplayed()
                onNodeWithText(text = "Version ${AppVersion.value}").assertDoesNotExist()
                onNodeWithContentDescription(label = "Back").performClick()
                onNode(matcher = hasText(text = "Tasks") and hasClickAction()).assertIsDisplayed()
                onNode(matcher = hasText(text = "Groups") and hasClickAction()).assertIsDisplayed()
                onNodeWithText(text = "Version ${AppVersion.value}").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `database error stays on hello and preserves existing file`() {
        val blocked = Files.writeString(temporaryDirectory.resolve("config"), "existing")
        withAppKoin(file = blocked.resolve("taskb.db")) { application ->
            val settings = application.koin.get<IAppSettings>()
            runComposeUiTest {
                setContent {
                    KoinIsolatedContext(context = application) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = settings) }
                    }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasText(text = "Couldn't read settings. Press Continue to get started."))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithText(text = "Continue").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasText(text = "Couldn't save settings. Please try again."))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithText(text = "Welcome to TaskB").assertIsDisplayed()
                onNode(matcher = hasText(text = "Tasks") and hasClickAction()).assertDoesNotExist()
                onNode(matcher = hasText(text = "Groups") and hasClickAction()).assertDoesNotExist()
            }
        }
        assertEquals(expected = "existing", actual = Files.readString(blocked))
    }
}
