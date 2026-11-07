package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.feature.event.testing.FakeEventRepository
import com.mkulikov.taskb.feature.event.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class EventRecurrenceInputUiTest {
    @Test
    fun `repeat numbers stay synchronous and save their latest values with a delayed store`() {
        for (wide in listOf(false, true)) {
            val scheduler = TestCoroutineScheduler()
            val repository = FakeEventRepository()
            val date = LocalDate(year = 2026, month = 11, day = 3)
            EventEditorStore(
                    repository = repository,
                    groups = FakeGroupRepository(),
                    initialDate = date,
                    dispatcher = StandardTestDispatcher(scheduler = scheduler),
                )
                .use { store ->
                    scheduler.runCurrent()
                    store.state.value.name.setTextAndPlaceCursorAtEnd(text = "Recurring item")
                    store.accept(
                        intent = EventEditorIntent.RecurrenceChanged(
                            recurrence = EventRecurrence.monthly(date = date).copy(occurrenceCount = 10)
                        )
                    )
                    scheduler.runCurrent()
                    runSkikoComposeUiTest(size = Size(width = if (wide) 1280f else 440f, height = 1100f)) {
                        setContent {
                            TaskBTheme(darkTheme = false) {
                                EventEditorContent(
                                    state = store.state.collectAsState().value,
                                    onIntent = store::accept,
                                    wide = wide,
                                    onBack = {},
                                    onChooseGroup = {},
                                )
                            }
                        }
                        val interval = onNodeWithContentDescription(label = "Repeat interval")
                        interval.performScrollTo().performTextReplacement(text = "")
                        for ((index, character) in "12".withIndex()) {
                            interval.performTextInput(text = character.toString())
                            interval.assertTextEquals("12".take(n = index + 1))
                            interval.assert(
                                matcher = SemanticsMatcher.expectValue(
                                    key = SemanticsProperties.TextSelectionRange,
                                    expectedValue = TextRange(index = index + 1),
                                )
                            )
                        }
                        interval.performTextInput(text = "x")
                        interval.performTextInput(text = "345")
                        interval.assertTextEquals("12")
                        interval.performTextInputSelection(selection = TextRange(index = 1))
                        interval.performTextInput(text = "3")
                        interval.assertTextEquals("132")
                        val day = onNodeWithContentDescription(label = "Repeat day")
                        day.performScrollTo().performTextReplacement(text = "31")
                        val count = onNodeWithContentDescription(label = "Repeat occurrence count")
                        count.performScrollTo().performTextReplacement(text = "")
                        count.performTextInput(text = "2")
                        count.performTextInput(text = "4")
                        count.assertTextEquals("24")
                        count.performTextInputSelection(selection = TextRange(index = 1))
                        store.accept(
                            intent = EventEditorIntent.RecurrenceChanged(
                                recurrence = requireNotNull(value = store.state.value.editedRecurrence)
                                    .copy(frequency = RecurrenceFrequency.Yearly)
                            )
                        )
                        scheduler.runCurrent()
                        waitForIdle()
                        count.assertTextEquals("24")
                        count.assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.TextSelectionRange,
                                expectedValue = TextRange(index = 1),
                            )
                        )
                        onNodeWithContentDescription(label = "Save event").performClick()
                        scheduler.runCurrent()
                        val saved = requireNotNull(value = repository.lastWrite?.recurrence)
                        assertEquals(expected = 132, actual = saved.interval)
                        assertEquals(expected = 31, actual = saved.dayOfMonth)
                        assertEquals(expected = 24, actual = saved.occurrenceCount)
                    }
                }
        }
    }
}
