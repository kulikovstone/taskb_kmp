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
            runSkikoComposeUiTest(size = Size(width = 280f, height = 300f)) {
                var clicks = 0
                setContent {
                    TaskBTheme(darkTheme = dark) {
                        Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            Column(
                                modifier = Modifier.padding(all = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
                            ) {
                                TaskBSelectionButton(
                                    label = "Monthly",
                                    selected = true,
                                    onClick = { clicks++ },
                                    modifier = Modifier.width(width = 216.dp),
                                )
                                TaskBSelectionButton(
                                    label = "Weekly",
                                    selected = false,
                                    onClick = { clicks++ },
                                    modifier = Modifier.width(width = 216.dp),
                                )
                                TaskBSelectionButton(
                                    label = "month",
                                    selected = false,
                                    onClick = { clicks++ },
                                    modifier = Modifier.width(width = 216.dp),
                                    icon = TaskBIcons.Add,
                                )
                                TaskBSelectionButton(
                                    label = "Disabled",
                                    selected = false,
                                    onClick = { clicks++ },
                                    modifier = Modifier.width(width = 216.dp),
                                    enabled = false,
                                )
                            }
                        }
                    }
                }
                onNodeWithText(text = "Monthly").assertIsSelected()
                onNodeWithText(text = "Weekly").assertIsNotSelected()
                for (label in listOf("Monthly", "Weekly", "month")) {
                    val button = onNodeWithText(text = label)
                    val idle = button.captureToImage().toPixelMap()
                    button.performMouseInput { moveTo(position = center) }
                    mainClock.advanceTimeBy(milliseconds = 250)
                    assertCornersUnchanged(idle = idle, active = button.captureToImage().toPixelMap())
                    button.performMouseInput { press() }
                    mainClock.advanceTimeBy(milliseconds = 500)
                    val pressed = button.captureToImage().toPixelMap()
                    assertCornersUnchanged(idle = idle, active = pressed)
                    assertNotEquals(
                        illegal = idle[idle.width / 2, idle.height - 5],
                        actual = pressed[pressed.width / 2, pressed.height - 5],
                        message = "Press feedback must be visible inside $label",
                    )
                    if (label == "Monthly") {
                        val output = Path.of(
                            ("build/reports/screenshots/selection-button-pressed-" +
                                "${if (dark) "dark" else "light"}.png")
                        )
                        Files.createDirectories(output.parent)
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                    }
                    button.performMouseInput {
                        release()
                        exit()
                    }
                    mainClock.advanceTimeBy(milliseconds = 500)
                }
                assertEquals(expected = 3, actual = clicks)
                onNodeWithText(text = "Disabled").assertIsNotEnabled().performMouseInput { click() }
                assertEquals(expected = 3, actual = clicks)
            }
        }
    }

    private fun assertCornersUnchanged(idle: PixelMap, active: PixelMap) {
        for (x in listOf(0, 1, idle.width - 2, idle.width - 1)) {
            for (y in listOf(0, 1, idle.height - 2, idle.height - 1)) {
                assertEquals(
                    expected = idle[x, y],
                    actual = active[x, y],
                    message = "Feedback must not reach outside the corner at ($x, $y)",
                )
            }
        }
    }
}
