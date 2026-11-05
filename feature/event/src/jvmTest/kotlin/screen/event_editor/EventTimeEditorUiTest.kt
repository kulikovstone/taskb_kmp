package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.event.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.event.testing.FakeEventRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import com.mkulikov.taskb.ui.theme.Palette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class EventTimeEditorUiTest {
    @Test fun `selected clock hours and minutes are visible in both themes`() {
        for (dark in listOf(false, true)) for (hour in listOf(9, 21)) runSkikoComposeUiTest(size = Size(440f, 800f)) {
            var selected: LocalTime? = null
            setContent {
                TaskBTheme(dark) {
                    Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                        EventTimePickerDialog("Choose start time", LocalTime(hour, 30), 800.dp, { selected = it }, {})
                    }
                }
            }
            fun assertVisibleSelection(description: String, name: String) {
                mainClock.advanceTimeBy(500)
                val output = Path.of("build/reports/screenshots/time-picker-$name-$hour-${if (dark) "dark" else "light"}.png")
                Files.createDirectories(output.parent)
                ImageIO.write(onNode(isDialog()).captureToImage().toAwtImage(), "png", output.toFile())
                val pixels = onNode(hasContentDescription(description) and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text),
                    useUnmergedTree = true).assertIsDisplayed().captureToImage().toPixelMap()
                var background = 0
                var foreground = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    when (pixels[x, y].toArgb()) {
                        Palette.Blue.a700.toArgb() -> background++
                        Palette.White.toArgb() -> foreground++
                    }
                }
                assertTrue(background > 0, "$description must have the blue selection background")
                assertTrue(foreground > 0, "$description must have a visible white numeral")
            }
            assertVisibleSelection("$hour hours", "hour")
            onNodeWithContentDescription("Select minutes").performClick()
            assertVisibleSelection("30 minutes", "minute")
            onNodeWithContentDescription("Apply time").performClick()
            assertEquals(LocalTime(hour, 30), selected)
        }
    }

    @Test fun `all day date range has no clear action and saves in both layouts and themes`() {
        for (wide in listOf(true, false)) for (dark in listOf(false, true)) {
            val scheduler = TestCoroutineScheduler()
            val repo = FakeEventRepository()
            val date = LocalDate(2026, 11, 6)
            EventEditorStore(repo, FakeGroupRepository(), initialDate = date, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(if (wide) 1280f else 400f, 980f)) {
                    setContent {
                        TaskBTheme(dark) {
                            val state = store.state.collectAsState().value
                            Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                Box {
                                    EventEditorContent(state, store::accept, wide, {}, {})
                                    EventEditorDialogs(state, state.today, store::accept, 980.dp)
                                }
                            }
                        }
                    }
                    onNodeWithContentDescription("Event name").performTextReplacement("Vacation")
                    if (wide) {
                        val name = onNodeWithContentDescription("Event name").fetchSemanticsNode().boundsInRoot
                        val group = onNodeWithContentDescription("Choose event group").fetchSemanticsNode().boundsInRoot
                        assertEquals(name.top, group.top, 0.5f)
                        val start = onNodeWithContentDescription("Choose date").fetchSemanticsNode().boundsInRoot
                        val end = onNodeWithContentDescription("Choose event end date").fetchSemanticsNode().boundsInRoot
                        assertEquals(start.top, end.top, 0.5f)
                    }
                    onNodeWithContentDescription("Clear date").assertDoesNotExist()
                    onNodeWithContentDescription("Choose event end date").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Choose November 8, 2026").performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Choose end time").assertDoesNotExist()
                    onNodeWithText("Duration").assertDoesNotExist()
                    onNodeWithText("Custom").assertDoesNotExist()
                    onNodeWithText("30 min").assertDoesNotExist()
                    onNodeWithContentDescription("Choose start time").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Apply time").performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Choose end time").performScrollTo().assertIsDisplayed()
                    for (label in listOf("15 min", "30 min", "1 hour")) onNodeWithText(label).assertDoesNotExist()
                    onNodeWithContentDescription("Clear start time").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    val output = Path.of("build/reports/screenshots/event-range-${if (wide) "wide" else "compact"}-${if (dark) "dark" else "light"}.png")
                    Files.createDirectories(output.parent)
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                    onNodeWithContentDescription("Save event").performClick()
                    scheduler.runCurrent()
                    assertEquals(3, repo.lastWrite?.durationDays)
                    assertNull(repo.lastWrite?.startTime)
                    assertNull(repo.lastWrite?.durationMinutes)
                }
            }
        }
    }

    @Test fun `time reveals an end time thirty minutes later and quick intervals update it`() {
        for (wide in listOf(true, false)) {
            val scheduler = TestCoroutineScheduler()
            val repo = FakeEventRepository()
            EventEditorStore(repo, FakeGroupRepository(), initialDate = LocalDate(2026, 11, 6), dispatcher = StandardTestDispatcher(scheduler)).use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(if (wide) 1280f else 400f, 980f)) {
                    setContent {
                        TaskBTheme(false) {
                            val state = store.state.collectAsState().value
                            Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                Box {
                                    EventEditorContent(state, store::accept, wide, {}, {})
                                    EventEditorDialogs(state, state.today, store::accept, 980.dp)
                                }
                            }
                        }
                    }
                    onNodeWithContentDescription("Event name").performTextReplacement("Meeting")
                    onNodeWithContentDescription("Choose start time").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Apply time").performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithText("30 min").performScrollTo().assertIsSelected()
                    onNode(hasContentDescription("Choose end time") and hasText("09:30")).performScrollTo().assertIsDisplayed()
                    onNodeWithText("Duration").assertDoesNotExist()
                    onNodeWithText("Custom").assertDoesNotExist()
                    val output = Path.of("build/reports/screenshots/event-time-range-${if (wide) "wide" else "compact"}.png")
                    Files.createDirectories(output.parent)
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                    onNodeWithText("15 min").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithText("15 min").assertIsSelected()
                    onNode(hasContentDescription("Choose end time") and hasText("09:15")).performScrollTo().assertIsDisplayed()
                    onNodeWithContentDescription("Save event").performClick()
                    scheduler.runCurrent()
                    assertEquals(15, repo.lastWrite?.durationMinutes)
                }
            }
        }
    }
}
