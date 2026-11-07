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
            runSkikoComposeUiTest(size = Size(width = if (wide) 768f else 400f, height = 900f)) {
                setContent {
                    TaskBTheme(darkTheme = dark) {
                        Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(state = rememberScrollState())
                                    .padding(all = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(space = 8.dp),
                            ) {
                                Text(
                                    text = "Description",
                                    style = TaskBTheme.typography.label,
                                    color = TaskBTheme.colors.text,
                                )
                                TaskBTextArea(
                                    state = state,
                                    description = "Task description",
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "Add a description",
                                    minLines = if (wide) 2 else 4,
                                )
                            }
                        }
                    }
                }
                val field = onNodeWithContentDescription(label = "Task description")
                assertEquals(
                    expected = if (wide) 68f else 112f,
                    actual = field.getUnclippedBoundsInRoot().height.value,
                    absoluteTolerance = 1f,
                )
                val suffix = "${if (wide) "wide" else "compact"}-${if (dark) "dark" else "light"}"
                screenshot(name = "empty-$suffix")
                field.performClick().performTextInput(text = longDescription)
                field.assertTextEquals(longDescription)
                val height = field.getUnclippedBoundsInRoot().height.value
                assertEquals(expected = if (wide) 310f else 442f, actual = height, absoluteTolerance = 1f)
                screenshot(name = "long-$suffix")
            }
        }
    }

    @Test
    fun `long text grows beyond the viewport and only the form scrolls`() {
        val state = TextFieldState()
        runSkikoComposeUiTest(size = Size(width = 400f, height = 360f)) {
            setContent {
                TaskBTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(tag = "form")
                            .verticalScroll(state = rememberScrollState())
                            .padding(all = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(space = 24.dp),
                    ) {
                        TaskBTextArea(
                            state = state,
                            description = "Task description",
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                        )
                        Text(text = "Next field")
                    }
                }
            }
            val field = onNodeWithContentDescription(label = "Task description")
            field.performTextInput(
                text = (1..40).joinToString(separator = "\n") { lineNumber ->
                    "Description line $lineNumber"
                },
            )
            assertTrue(actual = field.getUnclippedBoundsInRoot().height.value > 800f)
            val internalScroll = field.fetchSemanticsNode().config.getOrElseNullable(
                key = SemanticsProperties.VerticalScrollAxisRange
            ) {
                null
            }
            assertEquals(expected = 0f, actual = internalScroll?.maxValue?.invoke() ?: 0f)
            val formScroll =
                onNodeWithTag(testTag = "form")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(actual = formScroll.maxValue() > 0f)
            onNodeWithText(text = "Next field").performScrollTo().assertIsDisplayed()
        }
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val output = Path.of("build/reports/screenshots/description-$name.png")
        Files.createDirectories(output.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
    }

    private val longDescription =
        """
        Review the task creation and editing screens before implementation.
        Check the date picker, group selector and recurrence settings together.

        Design checklist:
        • Compare empty, filled and focused states.
        • Check text wrapping in the narrow layout.
        • Keep the main actions visible at the top of the screen.
        • Add examples for long task names and descriptions.

        Notes:
        ${"The description should remain readable while the task is being edited. " +
            "When more text is added, the field grows and the next sections move down."}
        Scroll the whole form to reach the date, group and repeat settings.
        """
            .trimIndent()
}
