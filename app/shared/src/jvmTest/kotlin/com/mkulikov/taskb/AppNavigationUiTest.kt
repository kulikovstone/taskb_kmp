package com.mkulikov.taskb

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class AppNavigationUiTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val temporaryDirectory: Path get() = temporaryFolder.root.toPath()

    @Test
    fun `continue opens home and persists completed welcome`() {
        val file = temporaryDirectory.resolve("taskb.db")
        withAppKoin(file) { application ->
            val settings = application.koin.get<IAppSettings>()
            runComposeUiTest {
                setContent {
                    KoinIsolatedContext(application) { TaskBTheme(darkTheme = false) { TaskBApp(settings) } }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("Continue")).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText("Welcome to TaskB").assertIsDisplayed()
                onNodeWithText("Continue").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("Tasks") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(hasText("Tasks") and hasClickAction()).assertIsDisplayed()
                onNode(hasText("Groups") and hasClickAction()).assertIsDisplayed()
                onNodeWithText("Home screen").assertDoesNotExist()
            }
        }
        withAppKoin(file) { application ->
            assertTrue(runBlocking { application.koin.get<IAppSettings>().hasCompletedWelcome() })
        }
    }

    @Test
    fun `returning user starts with home and can open tasks and groups and return`() {
        withAppKoin(temporaryDirectory.resolve("taskb.db")) { application ->
            val settings = application.koin.get<IAppSettings>()
            runBlocking { settings.completeWelcome() }
            runComposeUiTest {
                setContent {
                    KoinIsolatedContext(application) { TaskBTheme(darkTheme = true) { TaskBApp(settings) } }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("Tasks") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(hasText("Tasks") and hasClickAction()).assertIsDisplayed()
                onNode(hasText("Groups") and hasClickAction()).assertIsDisplayed()
                onNodeWithText("Home screen").assertDoesNotExist()
                onNodeWithText("Welcome to TaskB").assertDoesNotExist()
                val version = onNodeWithText("Version ${AppVersion.value}").assertIsDisplayed()
                val screenBounds = onRoot().fetchSemanticsNode().boundsInRoot
                val versionBounds = version.fetchSemanticsNode().boundsInRoot
                assertTrue(versionBounds.center.y > screenBounds.top + screenBounds.height * 0.8f)

                onNode(hasText("Tasks") and hasClickAction()).performClick()
                onNodeWithContentDescription("Back").assertIsDisplayed()
                onNodeWithText("Tasks").assertIsDisplayed()
                onNode(hasText("Groups") and hasClickAction()).assertDoesNotExist()
                onNodeWithContentDescription("Back").performClick()
                onNode(hasText("Tasks") and hasClickAction()).assertIsDisplayed()
                onNode(hasText("Groups") and hasClickAction()).assertIsDisplayed()
                onNodeWithText("Version ${AppVersion.value}").assertIsDisplayed()

                onNode(hasText("Groups") and hasClickAction()).performClick()
                onNodeWithContentDescription("Back").assertIsDisplayed()
                onNodeWithText("Groups").assertIsDisplayed()
                onNodeWithText("Version ${AppVersion.value}").assertDoesNotExist()
                onNodeWithContentDescription("Back").performClick()
                onNode(hasText("Tasks") and hasClickAction()).assertIsDisplayed()
                onNode(hasText("Groups") and hasClickAction()).assertIsDisplayed()
                onNodeWithText("Version ${AppVersion.value}").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `database error stays on hello and preserves existing file`() {
        val blocked = Files.writeString(temporaryDirectory.resolve("config"), "existing")
        withAppKoin(blocked.resolve("taskb.db")) { application ->
            val settings = application.koin.get<IAppSettings>()
            runComposeUiTest {
                setContent {
                    KoinIsolatedContext(application) { TaskBTheme(darkTheme = false) { TaskBApp(settings) } }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("Couldn't read settings. Press Continue to get started."))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText("Continue").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("Couldn't save settings. Please try again."))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText("Welcome to TaskB").assertIsDisplayed()
                onNode(hasText("Tasks") and hasClickAction()).assertDoesNotExist()
                onNode(hasText("Groups") and hasClickAction()).assertDoesNotExist()
            }
        }
        assertEquals("existing", Files.readString(blocked))
    }
}
