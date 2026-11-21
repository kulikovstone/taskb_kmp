package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalTestApi::class)
class CalendarTimedTasksUiTest {
    @Test
    fun `timed rows match the calendar design and every selected-day task appears in the sidebar`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
            val opened = mutableListOf<Int>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarTestScreen(
                        state = timedCalendarPreviewState(),
                        onIntent = {},
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                        onOpenEvent = { event -> opened.add(element = event.id) },
                    )
                }
            }
            onNodeWithText(text = "Recurring · 2").assertIsDisplayed()
            onNodeWithText(text = "Events · 3").assertIsDisplayed()
            onNodeWithText(text = "Tasks · 1").assertIsDisplayed()
            onNodeWithText(text = "Friday · 3 tasks · 3 events").assertIsDisplayed()
            onNode(
                    matcher = hasText(text = "more", substring = true) and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-11-06")),
                    useUnmergedTree = true,
                )
                .assertDoesNotExist()
            onNode(
                    matcher = hasTestTag(testTag = "calendar-task-303") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-11-06")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onNodeWithText(text = "12:00–13:00 (1 hour)").assertIsDisplayed()
            onNodeWithText(text = "15:00–15:30 (30 min)").assertIsDisplayed()
            onNodeWithText(text = "6 Nov, 18:00–9 Nov, 10:00 (2 days 16 hr)").assertIsDisplayed()
            for (id in 300..303) onNode(
                    matcher = hasTestTag(testTag = if (id == 303) "calendar-task-$id" else "calendar-event:$id") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onNodeWithContentDescription(label = "Complete Business trip", useUnmergedTree = true)
                .assertDoesNotExist()
            onNode(
                    matcher = hasContentDescription(value = "Complete Pay utility bills") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .assertExists()
            onAllNodesWithTag(testTag = "calendar-event-marker-301", useUnmergedTree = true)
                .assertCountEquals(expectedSize = 0)

            for ((day, label) in
                listOf(
                    6 to "18:00 Business trip",
                    7 to "Business trip",
                    8 to "Business trip",
                    9 to "Business trip 10:00",
                )) {
                onNode(
                        matcher = hasText(text = label) and
                            hasAnyAncestor(
                                matcher = hasTestTag(
                                    testTag =
                                        ("calendar-day-2026-11-" +
                                            "${day.toString().padStart(length = 2, padChar = '0')}")
                                )
                            ),
                        useUnmergedTree = true,
                    )
                    .assertIsDisplayed()
            }
            val active = onNodeWithText(text = "12:00 Team sync", useUnmergedTree = true).textLayout()
            assertEquals(
                expected = Palette.BlueGrey.s600,
                actual = active.layoutInput.text.spanStyles.single().item.color,
            )
            assertEquals(expected = Palette.BlueGrey.s900, actual = active.layoutInput.style.color)
            val end = onNodeWithText(text = "Business trip 10:00", useUnmergedTree = true).textLayout()
            assertEquals(expected = 14, actual = end.layoutInput.text.spanStyles.single().start)
            assertEquals(
                expected = Palette.BlueGrey.s600,
                actual = end.layoutInput.text.spanStyles.single().item.color,
            )
            val past = onNodeWithText(text = "16:00 Sprint review", useUnmergedTree = true).textLayout()
            assertEquals(
                expected = Palette.Grey.s400,
                actual = past.layoutInput.text.spanStyles.single().item.color,
            )
            assertEquals(expected = Palette.Grey.s400, actual = past.layoutInput.style.color)
            assertEquals(expected = TextDecoration.None, actual = past.layoutInput.style.textDecoration)
            assertEquals(
                expected = 2,
                actual = onNodeWithText(text = "11:00 Review mobile app design", useUnmergedTree = true)
                    .textLayout()
                    .lineCount,
            )
            assertEquals(
                expected = 1,
                actual = onNodeWithText(text = "6 Nov, 18:00–9 Nov, 10:00 (2 days 16 hr)", useUnmergedTree = true)
                    .textLayout()
                    .lineCount,
            )

            val coffee = onNodeWithTag(testTag = "calendar-event-time-301", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val team = onNodeWithTag(testTag = "calendar-event-time-300", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            assertEquals(expected = 24f, actual = coffee.left, absoluteTolerance = 0.5f)
            assertEquals(expected = 37f, actual = team.left, absoluteTolerance = 0.5f)
            val marker = onNode(
                    matcher = hasTestTag(testTag = "calendar-event-marker-300") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar")),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNode()
                .boundsInRoot
            assertEquals(expected = team.top + 3f, actual = marker.top, absoluteTolerance = 0.5f)
            assertEquals(expected = 52f, actual = marker.height, absoluteTolerance = 0.5f)

            onNode(
                    matcher = hasText(text = "Business trip") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-11-07")),
                    useUnmergedTree = true,
                )
                .performClick()
            runOnIdle { assertEquals(expected = listOf(element = 302), actual = opened) }
            val path = Path.of("build/reports/screenshots")
            Files.createDirectories(path)
            ImageIO.write(
                onRoot().captureToImage().toAwtImage(),
                "png",
                path.resolve("calendar-timed-tasks.png").toFile(),
            )
        }
    }

    @Test
    fun `dragging a continuing event shifts its start by the dragged day delta`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val intents = mutableListOf<CalendarIntent>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarTestScreen(
                        state = timedCalendarPreviewState(),
                        onIntent = intents::add,
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            val source = onNode(
                    matcher = hasTestTag(testTag = "calendar-event:302") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-11-07")),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            val target = onNodeWithTag(testTag = "calendar-day-2026-11-12", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            onRoot().performMouseInput {
                moveTo(position = source)
                press()
                moveTo(position = source + Offset(x = 28f, y = 0f))
                moveTo(position = target)
            }
            onNode(
                    matcher = hasText(text = "Business trip") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-drag-preview")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onRoot().performMouseInput { release() }
            runOnIdle {
                assertEquals(
                    expected = listOf<CalendarIntent>(
                        element = CalendarIntent.MoveEvent(
                            id = 302,
                            date = LocalDate(year = 2026, month = 11, day = 11),
                        )
                    ),
                    actual = intents,
                )
                assertTrue(actual = intents.none { intent -> intent is CalendarIntent.ToggleCompleted })
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(key = SemanticsActions.GetTextLayoutResult) { getLayoutResult ->
            getLayoutResult(results)
        }
        return results.single()
    }
}
