package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TaskBTextAreaUiTest {
    @Test
    fun `minimum height and wrapping match wide and compact designs in both themes`() {
        for (wide in listOf(true, false)) for (dark in listOf(false, true)) {
            val state = TextFieldState()
            runSkikoComposeUiTest(size = Size(if (wide) 768f else 400f, 900f)) {
                setContent {
                    TaskBTheme(dark) {
                        Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Description", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text)
                                TaskBTextArea(state, "Task description", Modifier.fillMaxWidth(), "Add a description", minLines = if (wide) 2 else 4)
                            }
                        }
                    }
                }
                val field = onNodeWithContentDescription("Task description")
                assertEquals(if (wide) 68f else 112f, field.getUnclippedBoundsInRoot().height.value, 1f)
                val suffix = "${if (wide) "wide" else "compact"}-${if (dark) "dark" else "light"}"
                screenshot("empty-$suffix")
                field.performClick().performTextInput(longDescription)
                field.assertTextEquals(longDescription)
                val height = field.getUnclippedBoundsInRoot().height.value
                assertEquals(if (wide) 310f else 442f, height, 1f)
                screenshot("long-$suffix")
            }
        }
    }

    @Test
    fun `long text grows beyond the viewport and only the form scrolls`() {
        val state = TextFieldState()
        runSkikoComposeUiTest(size = Size(400f, 360f)) {
            setContent {
                TaskBTheme {
                    Column(Modifier.fillMaxSize().testTag("form").verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        TaskBTextArea(state, "Task description", Modifier.fillMaxWidth(), minLines = 4)
                        Text("Next field")
                    }
                }
            }
            val field = onNodeWithContentDescription("Task description")
            field.performTextInput((1..40).joinToString("\n") { "Description line $it" })
            assertTrue(field.getUnclippedBoundsInRoot().height.value > 800f)
            val internalScroll = field.fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.VerticalScrollAxisRange) { null }
            assertEquals(0f, internalScroll?.maxValue?.invoke() ?: 0f)
            val formScroll = onNodeWithTag("form").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(formScroll.maxValue() > 0f)
            onNodeWithText("Next field").performScrollTo().assertIsDisplayed()
        }
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val output = Path.of("build/reports/screenshots/description-$name.png")
        Files.createDirectories(output.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
    }

    private val longDescription = """
        Review the task creation and editing screens before implementation.
        Check the date picker, group selector and recurrence settings together.

        Design checklist:
        • Compare empty, filled and focused states.
        • Check text wrapping in the narrow layout.
        • Keep the main actions visible at the top of the screen.
        • Add examples for long task names and descriptions.

        Notes:
        The description should remain readable while the task is being edited. When more text is added, the field grows and the next sections move down.
        Scroll the whole form to reach the date, group and repeat settings.
    """.trimIndent()
}
