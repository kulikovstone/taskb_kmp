package com.mkulikov.taskb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.core.navigation.AppRoot
import com.mkulikov.taskb.feature.calendar.api.CalendarRoute
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import kotlin.test.*
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class EventDateRangeNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `vacation saves without time and every calendar cell including the first and last shows only its title`() {
        val file = directory.root.toPath().resolve("vacation.db")
        val start = Clock.System.todayIn(TimeZone.currentSystemDefault()).yearMonth.onDay(6)
        val end = start.plus(3, DateTimeUnit.DAY)
        val format = LocalDate.Format { monthName(MonthNames.ENGLISH_FULL); chars(" "); day(Padding.NONE); chars(", "); year() }
        withAppKoin(file) { app ->
            runSkikoComposeUiTest(size = Size(1440f, 1100f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { AppRoot(CalendarRoute) } } }
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("calendar-grid")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("calendar-day-number-$start", useUnmergedTree = true).performClick()
                onNodeWithText("Event").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription("Event name")).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Event name").performTextInput("Vacation")
                onNodeWithContentDescription("Clear date").assertDoesNotExist()
                onNodeWithContentDescription("Choose event end date").performClick()
                onNodeWithContentDescription("Choose ${format.format(end)}").performClick()
                onNodeWithContentDescription("Save event").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("calendar-grid")).fetchSemanticsNodes().isNotEmpty() }
                for (offset in 0..3) {
                    val day = start.plus(offset, DateTimeUnit.DAY)
                    val cell = hasAnyAncestor(hasTestTag("calendar-day-$day"))
                    onNode(hasText("Vacation") and cell, useUnmergedTree = true).assertIsDisplayed()
                    onNode(hasText(":", substring = true) and cell, useUnmergedTree = true).assertDoesNotExist()
                }
                onNodeWithContentDescription("Complete Vacation").assertDoesNotExist()
                val middle = start.plus(1, DateTimeUnit.DAY)
                onNodeWithTag("calendar-day-number-$middle", useUnmergedTree = true).performClick()
                val source = onNode(hasText("Vacation") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot.center
                val targetDay = start.plus(6, DateTimeUnit.DAY)
                val target = onNodeWithTag("calendar-day-$targetDay", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                onRoot().performMouseInput { moveTo(source); press(); moveTo(source + Offset(28f, 0f)); moveTo(target); release() }
                waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() }
                val moved = runBlocking { app.koin.get<IEventRepository>().events.first().single() }
                assertEquals(start.plus(5, DateTimeUnit.DAY), moved.dueDate)
                assertEquals(end.plus(5, DateTimeUnit.DAY), moved.endDate)
                onNodeWithText("Undo").performClick()
                waitUntil(timeoutMillis = 10_000) { runBlocking { app.koin.get<IEventRepository>().events.first().single().dueDate == start } }
            }
        }
        withAppKoin(file) { app ->
            val event = runBlocking { app.koin.get<IEventRepository>().events.first().single() }
            assertEquals(start, event.dueDate)
            assertEquals(end, event.endDate)
            assertEquals(4, event.durationDays)
            assertNull(event.startTime)
            assertNull(event.durationMinutes)
        }
    }
}
