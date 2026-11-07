package com.mkulikov.taskb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.navigation.AppRoot
import com.mkulikov.taskb.feature.calendar.api.CalendarRoute
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.*
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext

@OptIn(ExperimentalTestApi::class)
class EventDateRangeNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `vacation saves without time and every calendar cell including the first and last shows only its title`() {
        val file = directory.root.toPath().resolve("vacation.db")
        val start = Clock.System.todayIn(timeZone = TimeZone.currentSystemDefault()).yearMonth.onDay(day = 6)
        val end = start.plus(value = 3, unit = DateTimeUnit.DAY)
        val format = LocalDate.Format {
            monthName(names = MonthNames.ENGLISH_FULL)
            chars(value = " ")
            day(padding = Padding.NONE)
            chars(value = ", ")
            year()
        }
        withAppKoin(file = file) { app ->
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 1100f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { AppRoot(startDestination = CalendarRoute) }
                    }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasTestTag(testTag = "calendar-grid")).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag(testTag = "calendar-day-number-$start", useUnmergedTree = true).performClick()
                onNodeWithText(text = "Event").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasContentDescription(value = "Event name"))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Event name").performTextInput(text = "Vacation")
                onNodeWithContentDescription(label = "Clear date").assertDoesNotExist()
                onNodeWithContentDescription(label = "Choose event end date").performClick()
                onNodeWithContentDescription(label = "Choose ${format.format(value = end)}").performClick()
                onNodeWithContentDescription(label = "Save event").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasTestTag(testTag = "calendar-grid")).fetchSemanticsNodes().isNotEmpty()
                }
                for (offset in 0..3) {
                    val day = start.plus(value = offset, unit = DateTimeUnit.DAY)
                    val cell = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$day"))
                    onNode(matcher = hasText(text = "Vacation") and cell, useUnmergedTree = true)
                        .assertIsDisplayed()
                    onNode(matcher = hasText(text = ":", substring = true) and cell, useUnmergedTree = true)
                        .assertDoesNotExist()
                }
                onNodeWithContentDescription(label = "Complete Vacation").assertDoesNotExist()
                val middle = start.plus(value = 1, unit = DateTimeUnit.DAY)
                onNodeWithTag(testTag = "calendar-day-number-$middle", useUnmergedTree = true).performClick()
                val source = onNode(
                        matcher = hasText(text = "Vacation") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .center
                val targetDay = start.plus(value = 6, unit = DateTimeUnit.DAY)
                val target = onNodeWithTag(testTag = "calendar-day-$targetDay", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .center
                onRoot().performMouseInput {
                    moveTo(position = source)
                    press()
                    moveTo(position = source + Offset(x = 28f, y = 0f))
                    moveTo(position = target)
                    release()
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(matcher = hasText(text = "Undo")).fetchSemanticsNodes().isNotEmpty()
                }
                val moved = runBlocking { app.koin.get<IEventRepository>().events.first().single() }
                assertEquals(expected = start.plus(value = 5, unit = DateTimeUnit.DAY), actual = moved.dueDate)
                assertEquals(expected = end.plus(value = 5, unit = DateTimeUnit.DAY), actual = moved.endDate)
                onNodeWithText(text = "Undo").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    runBlocking { app.koin.get<IEventRepository>().events.first().single().dueDate == start }
                }
            }
        }
        withAppKoin(file = file) { app ->
            val event = runBlocking { app.koin.get<IEventRepository>().events.first().single() }
            assertEquals(expected = start, actual = event.dueDate)
            assertEquals(expected = end, actual = event.endDate)
            assertEquals(expected = 4, actual = event.durationDays)
            assertNull(actual = event.startTime)
            assertNull(actual = event.durationMinutes)
        }
    }
}
