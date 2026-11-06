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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TaskBDatePickerUiTest {
    private val today = LocalDate(2026, 11, 1)

    @Test
    fun `anniversary can be chosen directly in both sizes and themes`() {
        for (width in listOf(360, 320)) for (dark in listOf(false, true)) {
            val date = mutableStateOf(LocalDate(2026, 11, 5))
            val state = DatePickerState(date.value.yearMonth)
            val chosen = mutableListOf<LocalDate>()
            runSkikoComposeUiTest(size = Size(width + 40f, 620f)) {
                setContent {
                    TaskBTheme(dark) {
                        Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.subtle) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                DatePickerContent(date.value, today, state, { chosen += it; date.value = it }, {}, Modifier.width(width.dp).heightIn(max = 580.dp))
                            }
                        }
                    }
                }
                val suffix = "$width-${if (dark) "dark" else "light"}"
                screenshot("calendar-$suffix")
                onNodeWithContentDescription("Choose year").performClick()
                onNodeWithText("2020–2039").assertIsDisplayed()
                onNodeWithContentDescription("Choose year 2026").assertIsSelected()
                screenshot("years-$suffix")
                onNodeWithContentDescription("Choose year 2036").performClick()
                onNodeWithContentDescription("Choose year").assert(hasText("2036"))
                onNodeWithContentDescription("Choose month").assert(hasText(if (width == 360) "November" else "Nov"))
                assertTrue(chosen.isEmpty())
                onNodeWithContentDescription("Choose month").performClick()
                screenshot("months-$suffix")
                onNodeWithContentDescription("Choose June 2036").performClick()
                onNodeWithContentDescription("Choose year").assert(hasText("2036"))
                assertTrue(chosen.isEmpty())
                onNodeWithContentDescription("Choose June 12, 2036").performClick()
                assertEquals(listOf(LocalDate(2036, 6, 12)), chosen)
                onNodeWithContentDescription("Choose June 12, 2036").assertIsSelected()
                screenshot("selected-$suffix")
            }
        }
    }

    @Test
    fun `year input validates supports Enter and Go and today does not select a date`() {
        val state = DatePickerState(today.yearMonth)
        val chosen = mutableListOf<LocalDate>()
        var dismissed = 0
        runSkikoComposeUiTest(size = Size(400f, 660f)) {
            setContent { TaskBTheme(false) { DatePickerContent(null, today, state, chosen::add, { dismissed++ }, Modifier.width(360.dp).heightIn(max = 640.dp)) } }
            onNodeWithContentDescription("Choose year").performClick()
            onNodeWithContentDescription("Year").performTextReplacement("0")
            onNodeWithText("Go").performClick()
            onNodeWithText("Enter a year between 1 and 9999.").assertIsDisplayed()
            onNodeWithContentDescription("Year").performTextReplacement("2036")
            onNodeWithText("Enter a year between 1 and 9999.").assertDoesNotExist()
            onNodeWithContentDescription("Year").performImeAction()
            onNodeWithContentDescription("Choose year").assert(hasText("2036"))
            onNodeWithContentDescription("Choose year").performClick()
            onNodeWithContentDescription("Year").performTextReplacement("2055")
            onNodeWithText("Go").performClick()
            onNodeWithContentDescription("Choose year").performClick()
            onNodeWithText("2040–2059").assertIsDisplayed()
            onNodeWithContentDescription("Previous 20 years").performClick()
            onNodeWithText("2020–2039").assertIsDisplayed()
            onNodeWithContentDescription("Choose year 2024").performClick()
            onNodeWithText("Today").performClick()
            onNodeWithContentDescription("Choose year").assert(hasText("2026"))
            onNodeWithText("Cancel").performClick()
            assertEquals(1, dismissed)
            assertTrue(chosen.isEmpty())
        }
    }

    @Test
    fun `short narrow dialog scrolls its grids and keeps actions reachable`() {
        val open = mutableStateOf(true)
        var selected: LocalDate? = null
        runSkikoComposeUiTest(size = Size(300f, 320f)) {
            setContent {
                TaskBTheme(false) {
                    Surface(Modifier.fillMaxSize()) {
                        if (open.value) TaskBDatePicker(null, today, { selected = it; open.value = false }, { open.value = false }, 320.dp)
                    }
                }
            }
            val bounds = onNodeWithTag("date-picker-panel").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width <= 300f)
            assertTrue(bounds.height <= 272f)
            onNodeWithContentDescription("Choose year").performClick()
            onNodeWithText("Cancel").assertIsDisplayed()
            onNodeWithText("Today").assertIsDisplayed()
            onNodeWithContentDescription("Choose year 2039").performScrollTo().performClick()
            onNodeWithContentDescription("Choose month").performClick()
            onNodeWithContentDescription("Choose June 2039").performScrollTo().performClick()
            onNodeWithContentDescription("Choose June 12, 2039").performScrollTo().performClick()
            assertEquals(LocalDate(2039, 6, 12), selected)
            onNode(isDialog()).assertDoesNotExist()
        }
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val output = Path.of("build/reports/screenshots/date-picker-$name.png")
        Files.createDirectories(output.parent)
        ImageIO.write(onNodeWithTag("date-picker-panel").captureToImage().toAwtImage(), "png", output.toFile())
    }
}
