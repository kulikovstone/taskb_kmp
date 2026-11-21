package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.event.component.EventItemEditorSchedule
import com.mkulikov.taskb.feature.event.testing.FakeEventRepository
import com.mkulikov.taskb.feature.event.testing.FakeGroupRepository
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class EventTimeEditorUiTest {
    @Test
    fun `selected clock hours and minutes are visible in both themes`() {
        for (dark in listOf(false, true)) for (hour in listOf(9, 21)) runSkikoComposeUiTest(
            size = Size(width = 440f, height = 800f)
        ) {
            var selected: LocalTime? = null
            setContent {
                TaskBTheme(darkTheme = dark) {
                    Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                        EventTimePickerDialog(
                            title = "Choose start time",
                            initialTime = LocalTime(hour = hour, minute = 30),
                            availableHeight = 800.dp,
                            onConfirm = { time -> selected = time },
                            onDismiss = {},
                        )
                    }
                }
            }
            fun assertVisibleSelection(description: String, name: String) {
                mainClock.advanceTimeBy(milliseconds = 500)
                val output = Path.of(
                    "build/reports/screenshots/time-picker-$name-$hour-${if (dark) "dark" else "light"}.png"
                )
                Files.createDirectories(output.parent)
                ImageIO.write(onNode(matcher = isDialog()).captureToImage().toAwtImage(), "png", output.toFile())
                val pixels = onNode(
                        matcher = hasContentDescription(value = description) and
                            SemanticsMatcher.keyNotDefined(key = SemanticsProperties.Text),
                        useUnmergedTree = true,
                    )
                    .assertIsDisplayed()
                    .captureToImage()
                    .toPixelMap()
                var background = 0
                var foreground = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    when (pixels[x, y].toArgb()) {
                        Palette.Blue.a700.toArgb() -> background++
                        Palette.White.toArgb() -> foreground++
                    }
                }
                assertTrue(
                    actual = background > 0,
                    message = "$description must have the blue selection background",
                )
                assertTrue(actual = foreground > 0, message = "$description must have a visible white numeral")
            }
            assertVisibleSelection(description = "$hour hours", name = "hour")
            onNodeWithContentDescription(label = "Select minutes").performClick()
            assertVisibleSelection(description = "30 minutes", name = "minute")
            onNodeWithContentDescription(label = "Apply time").performClick()
            assertEquals(expected = LocalTime(hour = hour, minute = 30), actual = selected)
        }
    }

    @Test
    fun `all day date range has no clear action and saves in both themes at different widths`() {
        for (wide in listOf(true, false)) for (dark in listOf(false, true)) {
            val scheduler = TestCoroutineScheduler()
            val repo = FakeEventRepository()
            val date = LocalDate(year = 2026, month = 11, day = 6)
            EventEditorStore(
                    repository = repo,
                    groups = FakeGroupRepository(),
                    initialDate = date,
                    dispatcher = StandardTestDispatcher(scheduler = scheduler),
                )
                .use { store ->
                    scheduler.runCurrent()
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Vacation")
                    runSkikoComposeUiTest(size = Size(width = if (wide) 1280f else 400f, height = 980f)) {
                        setContent {
                            TaskBTheme(darkTheme = dark) {
                                val state = store.state.collectAsState().value
                                Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                    Box {
                                        EventScheduleTestContent(state = state, onIntent = store::accept)
                                        EventEditorDialogs(
                                            state = state,
                                            today = state.today,
                                            onIntent = store::accept,
                                            availableHeight = 980.dp,
                                        )
                                    }
                                }
                            }
                        }
                        val start = onNodeWithContentDescription(label = "Choose date")
                            .fetchSemanticsNode().boundsInRoot
                        val end = onNodeWithContentDescription(label = "Choose event end date")
                            .fetchSemanticsNode().boundsInRoot
                        assertEquals(expected = start.top, actual = end.top, absoluteTolerance = 0.5f)
                        onNodeWithContentDescription(label = "Clear date").assertDoesNotExist()
                        onNodeWithContentDescription(label = "Choose event end date")
                            .performScrollTo()
                            .performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithContentDescription(label = "Choose November 8, 2026").performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithContentDescription(label = "Choose end time").assertDoesNotExist()
                        onNodeWithText(text = "Duration").assertDoesNotExist()
                        onNodeWithText(text = "Custom").assertDoesNotExist()
                        onNodeWithText(text = "30 min").assertDoesNotExist()
                        onNodeWithContentDescription(label = "Choose start time").performScrollTo().performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithContentDescription(label = "Apply time").performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithContentDescription(label = "Choose end time")
                            .performScrollTo()
                            .assertIsDisplayed()
                        for (label in listOf("15 min", "30 min", "1 hour")) onNodeWithText(text = label)
                            .assertDoesNotExist()
                        onNodeWithContentDescription(label = "Clear start time").performScrollTo().performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        val output = Path.of(
                            ("build/reports/screenshots/event-range-" +
                                "${if (wide) "wide" else "compact"}-${if (dark) "dark" else "light"}." +
                                "png")
                        )
                        Files.createDirectories(output.parent)
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                        store.accept(intent = EventEditorIntent.Save)
                        scheduler.runCurrent()
                        assertEquals(expected = 3, actual = repo.lastWrite?.durationDays)
                        assertNull(actual = repo.lastWrite?.startTime)
                        assertNull(actual = repo.lastWrite?.durationMinutes)
                    }
                }
        }
    }

    @Test
    fun `time reveals an end time thirty minutes later and quick intervals update it`() {
        for (wide in listOf(true, false)) {
            val scheduler = TestCoroutineScheduler()
            val repo = FakeEventRepository()
            EventEditorStore(
                    repository = repo,
                    groups = FakeGroupRepository(),
                    initialDate = LocalDate(year = 2026, month = 11, day = 6),
                    dispatcher = StandardTestDispatcher(scheduler = scheduler),
                )
                .use { store ->
                    scheduler.runCurrent()
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Meeting")
                    runSkikoComposeUiTest(size = Size(width = if (wide) 1280f else 400f, height = 980f)) {
                        setContent {
                            TaskBTheme(darkTheme = false) {
                                val state = store.state.collectAsState().value
                                Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                    Box {
                                        EventScheduleTestContent(state = state, onIntent = store::accept)
                                        EventEditorDialogs(
                                            state = state,
                                            today = state.today,
                                            onIntent = store::accept,
                                            availableHeight = 980.dp,
                                        )
                                    }
                                }
                            }
                        }
                        onNodeWithContentDescription(label = "Choose start time").performScrollTo().performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithContentDescription(label = "Apply time").performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithText(text = "30 min").performScrollTo().assertIsSelected()
                        onNode(
                                matcher = hasContentDescription(value = "Choose end time") and hasText(text = "09:30")
                            )
                            .performScrollTo()
                            .assertIsDisplayed()
                        onNodeWithText(text = "Duration").assertDoesNotExist()
                        onNodeWithText(text = "Custom").assertDoesNotExist()
                        val output = Path.of(
                            "build/reports/screenshots/event-time-range-${if (wide) "wide" else "compact"}.png"
                        )
                        Files.createDirectories(output.parent)
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                        onNodeWithText(text = "15 min").performScrollTo().performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithText(text = "15 min").assertIsSelected()
                        onNode(
                                matcher = hasContentDescription(value = "Choose end time") and hasText(text = "09:15")
                            )
                            .performScrollTo()
                            .assertIsDisplayed()
                        store.accept(intent = EventEditorIntent.Save)
                        scheduler.runCurrent()
                        assertEquals(expected = 15, actual = repo.lastWrite?.durationMinutes)
                    }
                }
        }
    }
}

@Composable
internal fun EventScheduleTestContent(state: EventEditorState, onIntent: (EventEditorIntent) -> Unit) {
    Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
        EventItemEditorSchedule(state = state, onIntent = onIntent)
    }
}
