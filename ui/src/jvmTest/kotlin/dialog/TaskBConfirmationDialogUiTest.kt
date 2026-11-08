package com.mkulikov.taskb.ui.dialog

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TaskBConfirmationDialogUiTest {
    @Test
    fun `confirmation panel caps its width and keeps actions inside its padding`() {
        val message = "The group will be deleted. Its tasks will be kept."
        for (width in listOf(1440f, 320f)) {
            runSkikoComposeUiTest(size = Size(width = width, height = 900f)) {
                setContent {
                    TaskBTheme(darkTheme = false) {
                        TaskBConfirmationDialog(
                            title = "Delete group",
                            message = message,
                            confirmText = "Delete",
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }

                val panel = onNodeWithTag(testTag = "taskb-dialog-panel")
                val dialog = panel.getUnclippedBoundsInRoot()
                val body = onNodeWithText(text = message).getUnclippedBoundsInRoot()
                val cancel = onNodeWithText(text = "Cancel").assertIsDisplayed().getUnclippedBoundsInRoot()
                val confirm = onNodeWithText(text = "Delete").assertIsDisplayed().getUnclippedBoundsInRoot()

                assertEquals(expected = minOf(width - 32f, 400f).dp, actual = dialog.width)
                assertEquals(expected = 24.dp, actual = body.left - dialog.left)
                assertEquals(expected = 24.dp, actual = dialog.right - confirm.right)
                assertTrue(actual = dialog.bottom - confirm.bottom >= 14.dp)
                assertEquals(expected = 8.dp, actual = confirm.left - cancel.right)
                if (width == 1440f) {
                    assertEquals(expected = 148.dp, actual = dialog.height)
                    val output = Path.of("build/reports/screenshots/delete-confirmation.png")
                    Files.createDirectories(output.parent)
                    ImageIO.write(panel.captureToImage().toAwtImage(), "png", output.toFile())
                }
            }
        }
    }
}
