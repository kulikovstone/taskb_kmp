package com.mkulikov.taskb.ui.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class TaskBSelectionButtonUiTest {
    @Test
    fun `hover and press feedback follows rounded corners in both themes`() {
        for (dark in listOf(false, true)) {
            runSkikoComposeUiTest(size = Size(280f, 300f)) {
                var clicks = 0
                setContent {
                    TaskBTheme(dark) {
                        Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                TaskBSelectionButton("Monthly", true, { clicks++ }, Modifier.width(216.dp))
                                TaskBSelectionButton("Weekly", false, { clicks++ }, Modifier.width(216.dp))
                                TaskBSelectionButton("month", false, { clicks++ }, Modifier.width(216.dp), icon = TaskBIcons.Add)
                                TaskBSelectionButton("Disabled", false, { clicks++ }, Modifier.width(216.dp), enabled = false)
                            }
                        }
                    }
                }
                onNodeWithText("Monthly").assertIsSelected()
                onNodeWithText("Weekly").assertIsNotSelected()
                for (label in listOf("Monthly", "Weekly", "month")) {
                    val button = onNodeWithText(label)
                    val idle = button.captureToImage().toPixelMap()
                    button.performMouseInput { moveTo(center) }
                    mainClock.advanceTimeBy(250)
                    assertCornersUnchanged(idle, button.captureToImage().toPixelMap())
                    button.performMouseInput { press() }
                    mainClock.advanceTimeBy(500)
                    val pressed = button.captureToImage().toPixelMap()
                    assertCornersUnchanged(idle, pressed)
                    assertNotEquals(idle[idle.width / 2, idle.height - 5], pressed[pressed.width / 2, pressed.height - 5], "Press feedback must be visible inside $label")
                    if (label == "Monthly") {
                        val output = Path.of("build/reports/screenshots/selection-button-pressed-${if (dark) "dark" else "light"}.png")
                        Files.createDirectories(output.parent)
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                    }
                    button.performMouseInput { release(); exit() }
                    mainClock.advanceTimeBy(500)
                }
                assertEquals(3, clicks)
                onNodeWithText("Disabled").assertIsNotEnabled().performMouseInput { click() }
                assertEquals(3, clicks)
            }
        }
    }

    private fun assertCornersUnchanged(idle: PixelMap, active: PixelMap) {
        for (x in listOf(0, 1, idle.width - 2, idle.width - 1)) {
            for (y in listOf(0, 1, idle.height - 2, idle.height - 1)) {
                assertEquals(idle[x, y], active[x, y], "Feedback must not reach outside the corner at ($x, $y)")
            }
        }
    }
}
