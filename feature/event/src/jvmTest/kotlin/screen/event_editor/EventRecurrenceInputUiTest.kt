package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.event.testing.FakeEventRepository
import com.mkulikov.taskb.feature.event.testing.FakeGroupRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class EventRecurrenceInputUiTest {
    @Test
    fun `repeat numbers stay synchronous and save their latest values with a delayed store`() {
        for (wide in listOf(false, true)) {
            val scheduler = TestCoroutineScheduler()
            val repository = FakeEventRepository()
            val date = LocalDate(2026, 11, 3)
            EventEditorStore(repository, FakeGroupRepository(), initialDate = date,
                dispatcher = StandardTestDispatcher(scheduler)).use { store ->
                scheduler.runCurrent()
                store.state.value.name.setTextAndPlaceCursorAtEnd("Recurring item")
                store.accept(EventEditorIntent.RecurrenceChanged(EventRecurrence.monthly(date).copy(occurrenceCount = 10)))
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(if (wide) 1280f else 440f, 1100f)) {
                    setContent {
                        TaskBTheme(false) {
                            EventEditorContent(store.state.collectAsState().value, store::accept, wide, {}, {})
                        }
                    }
                    val interval = onNodeWithContentDescription("Repeat interval")
                    interval.performScrollTo().performTextReplacement("")
                    for ((index, character) in "12".withIndex()) {
                        interval.performTextInput(character.toString())
                        interval.assertTextEquals("12".take(index + 1))
                        interval.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(index + 1)))
                    }
                    interval.performTextInput("x")
                    interval.performTextInput("345")
                    interval.assertTextEquals("12")
                    interval.performTextInputSelection(TextRange(1))
                    interval.performTextInput("3")
                    interval.assertTextEquals("132")
                    val day = onNodeWithContentDescription("Repeat day")
                    day.performScrollTo().performTextReplacement("31")
                    val count = onNodeWithContentDescription("Repeat occurrence count")
                    count.performScrollTo().performTextReplacement("")
                    count.performTextInput("2")
                    count.performTextInput("4")
                    count.assertTextEquals("24")
                    count.performTextInputSelection(TextRange(1))
                    store.accept(EventEditorIntent.RecurrenceChanged(requireNotNull(store.state.value.editedRecurrence)
                        .copy(frequency = RecurrenceFrequency.Yearly)))
                    scheduler.runCurrent()
                    waitForIdle()
                    count.assertTextEquals("24")
                    count.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(1)))
                    onNodeWithContentDescription("Save event").performClick()
                    scheduler.runCurrent()
                    val saved = requireNotNull(repository.lastWrite?.recurrence)
                    assertEquals(132, saved.interval)
                    assertEquals(31, saved.dayOfMonth)
                    assertEquals(24, saved.occurrenceCount)
                }
            }
        }
    }
}
