package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

@OptIn(ExperimentalTestApi::class)
class TaskBDatePickerUiTest {
    private val today = LocalDate(year = 2026, month = 11, day = 1)

    @Test
    fun `anniversary can be chosen directly in both sizes and themes`() {
        for (width in listOf(360, 320)) for (dark in listOf(false, true)) {
            val date = mutableStateOf(value = LocalDate(year = 2026, month = 11, day = 5))
            val state = DatePickerState(initialMonth = date.value.yearMonth)
            val chosen = mutableListOf<LocalDate>()
            runSkikoComposeUiTest(size = Size(width = width + 40f, height = 620f)) {
                setContent {
                    TaskBTheme(darkTheme = dark) {
                        Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.subtle) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                DatePickerContent(
                                    date = date.value,
                                    today = today,
                                    state = state,
                                    onChoose = { candidateDate ->
                                        chosen += candidateDate
                                        date.value = candidateDate
                                    },
                                    onDismiss = {},
                                    modifier = Modifier.width(width = width.dp).heightIn(max = 580.dp),
                                )
                            }
                        }
                    }
                }
                val suffix = "$width-${if (dark) "dark" else "light"}"
                screenshot(name = "calendar-$suffix")
                onNodeWithContentDescription(label = "Choose year").performClick()
                onNodeWithText(text = "2020–2039").assertIsDisplayed()
                onNodeWithContentDescription(label = "Choose year 2026").assertIsSelected()
                screenshot(name = "years-$suffix")
                onNodeWithContentDescription(label = "Choose year 2036").performClick()
                onNodeWithContentDescription(label = "Choose year").assert(matcher = hasText(text = "2036"))
                onNodeWithContentDescription(label = "Choose month")
                    .assert(matcher = hasText(text = if (width == 360) "November" else "Nov"))
                assertTrue(actual = chosen.isEmpty())
                onNodeWithContentDescription(label = "Choose month").performClick()
                screenshot(name = "months-$suffix")
                onNodeWithContentDescription(label = "Choose June 2036").performClick()
                onNodeWithContentDescription(label = "Choose year").assert(matcher = hasText(text = "2036"))
                assertTrue(actual = chosen.isEmpty())
                onNodeWithContentDescription(label = "Choose June 12, 2036").performClick()
                assertEquals(
                    expected = listOf(element = LocalDate(year = 2036, month = 6, day = 12)),
                    actual = chosen,
                )
                onNodeWithContentDescription(label = "Choose June 12, 2036").assertIsSelected()
                screenshot(name = "selected-$suffix")
            }
        }
    }

    @Test
    fun `year input validates supports Enter and Go and today does not select a date`() {
        val state = DatePickerState(initialMonth = today.yearMonth)
        val chosen = mutableListOf<LocalDate>()
        var dismissed = 0
        runSkikoComposeUiTest(size = Size(width = 400f, height = 660f)) {
            setContent {
                TaskBTheme(darkTheme = false) {
                    DatePickerContent(
                        date = null,
                        today = today,
                        state = state,
                        onChoose = chosen::add,
                        onDismiss = { dismissed++ },
                        modifier = Modifier.width(width = 360.dp).heightIn(max = 640.dp),
                    )
                }
            }
            onNodeWithContentDescription(label = "Choose year").performClick()
            onNodeWithContentDescription(label = "Year").performTextReplacement(text = "0")
            onNodeWithText(text = "Go").performClick()
            onNodeWithText(text = "Enter a year between 1 and 9999.").assertIsDisplayed()
            onNodeWithContentDescription(label = "Year").performTextReplacement(text = "2036")
            onNodeWithText(text = "Enter a year between 1 and 9999.").assertDoesNotExist()
            onNodeWithContentDescription(label = "Year").performImeAction()
            onNodeWithContentDescription(label = "Choose year").assert(matcher = hasText(text = "2036"))
            onNodeWithContentDescription(label = "Choose year").performClick()
            onNodeWithContentDescription(label = "Year").performTextReplacement(text = "2055")
            onNodeWithText(text = "Go").performClick()
            onNodeWithContentDescription(label = "Choose year").performClick()
            onNodeWithText(text = "2040–2059").assertIsDisplayed()
            onNodeWithContentDescription(label = "Previous 20 years").performClick()
            onNodeWithText(text = "2020–2039").assertIsDisplayed()
            onNodeWithContentDescription(label = "Choose year 2024").performClick()
            onNodeWithText(text = "Today").performClick()
            onNodeWithContentDescription(label = "Choose year").assert(matcher = hasText(text = "2026"))
            onNodeWithText(text = "Cancel").performClick()
            assertEquals(expected = 1, actual = dismissed)
            assertTrue(actual = chosen.isEmpty())
        }
    }

    @Test
    fun `short narrow dialog scrolls its grids and keeps actions reachable`() {
        val open = mutableStateOf(value = true)
        var selected: LocalDate? = null
        runSkikoComposeUiTest(size = Size(width = 300f, height = 320f)) {
            setContent {
                TaskBTheme(darkTheme = false) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (open.value)
                            TaskBDatePicker(
                                date = null,
                                today = today,
                                onChoose = { date ->
                                    selected = date
                                    open.value = false
                                },
                                onDismiss = { open.value = false },
                                availableHeight = 380.dp,
                            )
                    }
                }
            }
            val bounds = onNodeWithTag(testTag = "date-picker-panel").fetchSemanticsNode().boundsInRoot
            assertTrue(actual = bounds.width <= 300f)
            assertTrue(actual = bounds.height <= 272f)
            onNodeWithContentDescription(label = "Choose year").performClick()
            onNodeWithText(text = "Cancel").assertIsDisplayed()
            onNodeWithText(text = "Today").assertIsDisplayed()
            onNodeWithContentDescription(label = "Choose year 2039").performScrollTo().performClick()
            onNodeWithContentDescription(label = "Choose month").performClick()
            onNodeWithContentDescription(label = "Choose June 2039").performScrollTo().performClick()
            onNodeWithContentDescription(label = "Choose June 12, 2039").performScrollTo().performClick()
            assertEquals(expected = LocalDate(year = 2039, month = 6, day = 12), actual = selected)
            onNode(matcher = isDialog()).assertDoesNotExist()
        }
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val output = Path.of("build/reports/screenshots/date-picker-$name.png")
        Files.createDirectories(output.parent)
        ImageIO.write(
            onNodeWithTag(testTag = "date-picker-panel").captureToImage().toAwtImage(),
            "png",
            output.toFile(),
        )
    }
}
