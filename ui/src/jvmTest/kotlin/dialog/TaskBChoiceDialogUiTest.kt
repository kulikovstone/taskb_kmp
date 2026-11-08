package com.mkulikov.taskb.ui.dialog

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TaskBChoiceDialogUiTest {
    @Test
    fun `recurring delete choices stay inside the shared panel in wide and short windows`() {
        val labels = listOf("Only this event", "This and following events", "Entire event and all occurrences")
        for (size in listOf(Size(width = 1440f, height = 900f), Size(width = 320f, height = 240f))) {
            var selected: String? = null
            runSkikoComposeUiTest(size = size) {
                setContent {
                    TaskBTheme(darkTheme = false) {
                        TaskBChoiceDialog(
                            title = "Delete repeating event",
                            message = "Choose which occurrences to delete.",
                            choices = labels.map { label ->
                                TaskBDialogChoice(
                                    label = label,
                                    onClick = { selected = label },
                                    destructive = true,
                                )
                            },
                            onDismiss = {},
                        )
                    }
                }

                val panel = onNodeWithTag(testTag = "taskb-dialog-panel")
                val bounds = panel.getUnclippedBoundsInRoot()
                val cancel = onNodeWithText(text = "Cancel").assertIsDisplayed().getUnclippedBoundsInRoot()
                val lastChoice = onNodeWithText(text = labels.last())
                lastChoice.performScrollTo().assertIsDisplayed()
                val lastChoiceBounds = lastChoice.getUnclippedBoundsInRoot()

                assertEquals(expected = minOf(size.width - 32f, 400f).dp, actual = bounds.width)
                assertEquals(expected = 24.dp, actual = bounds.right - cancel.right)
                assertEquals(expected = 24.dp, actual = lastChoiceBounds.left - bounds.left)
                assertEquals(expected = 24.dp, actual = bounds.right - lastChoiceBounds.right)
                assertTrue(actual = lastChoiceBounds.bottom <= cancel.top)
                if (size.width == 1440f) {
                    val output = Path.of("build/reports/screenshots/delete-repeating-event.png")
                    Files.createDirectories(output.parent)
                    ImageIO.write(panel.captureToImage().toAwtImage(), "png", output.toFile())
                }
                lastChoice.performClick()
                assertEquals(expected = labels.last(), actual = selected)
            }
        }
    }
}
