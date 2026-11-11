package com.mkulikov.taskb

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
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
    fun `welcome repeats on launch until disabled in settings`() {
        val file = temporaryDirectory.resolve("taskb.db")
        repeat(times = 2) { launch ->
            val width = if (launch == 0) 1440f else 400f
            val imageHeight = if (launch == 0) 900f else 636f
            withAppKoin(file = file) { application ->
                val settings = application.koin.get<IAppSettings>()
                runSkikoComposeUiTest(size = Size(width = width, height = 900f)) {
                    setContent {
                        KoinIsolatedContext(context = application) {
                            TaskBTheme(darkTheme = false) { TaskBApp(settings = settings) }
                        }
                    }
                    waitUntil(timeoutMillis = 10_000) {
                        onAllNodes(matcher = hasText(text = "Get started")).fetchSemanticsNodes().isNotEmpty()
                    }
                    onNodeWithText(text = "Welcome to TaskB").assertIsDisplayed()
                    val screenshot = Path.of("build/reports/screenshots/welcome-${width.toInt()}.png")
                    Files.createDirectories(screenshot.parent)
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", screenshot.toFile())
                    assertEquals(
                        expected = Rect(left = 0f, top = 0f, right = width, bottom = imageHeight),
                        actual = onNodeWithTag(testTag = "welcome-illustration").fetchSemanticsNode().boundsInRoot,
                    )
                    onNodeWithText(text = "Get started").performClick()
                    val settingsButton = hasText(text = "Settings") and hasClickAction()
                    waitUntil(timeoutMillis = 10_000) {
                        onAllNodes(matcher = settingsButton).fetchSemanticsNodes().isNotEmpty()
                    }
                    if (launch == 1) {
                        onNode(matcher = settingsButton).performClick()
                        val preference = hasText(text = "Show welcome screen at startup")
                        waitUntil(timeoutMillis = 10_000) {
                            onAllNodes(matcher = preference and isOn()).fetchSemanticsNodes().isNotEmpty()
                        }
                        onNode(matcher = preference and isOn()).performClick()
                        waitUntil(timeoutMillis = 10_000) {
                            onAllNodes(matcher = preference and isOff()).fetchSemanticsNodes().isNotEmpty()
                        }
                    }
                }
            }
        }
        withAppKoin(file = file) { application ->
            val settings = application.koin.get<IAppSettings>()
            runComposeUiTest {
                setContent {
                    KoinIsolatedContext(context = application) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = settings) }
                    }
                }
                val tasks = hasText(text = "Tasks") and hasClickAction()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = tasks).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(matcher = tasks).assertIsDisplayed()
                onNodeWithText(text = "Welcome to TaskB").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `returning user starts with home and can open tasks and groups and return`() {
        withAppKoin(file = temporaryDirectory.resolve("taskb.db")) { application ->
            val settings = application.koin.get<IAppSettings>()
            runBlocking { settings.setShowWelcomeScreen(enabled = false) }
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
    fun `settings read error is shown on welcome and continue opens home`() {
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
                    onAllNodes(matcher = hasText(text = "Couldn't read settings."))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithText(text = "Get started").performClick()
                val tasks = hasText(text = "Tasks") and hasClickAction()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = tasks).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(matcher = tasks).assertIsDisplayed()
            }
        }
        assertEquals(expected = "existing", actual = Files.readString(blocked))
    }
}
