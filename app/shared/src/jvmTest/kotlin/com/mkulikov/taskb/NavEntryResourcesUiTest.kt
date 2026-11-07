package com.mkulikov.taskb

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mkulikov.taskb.core.navigation.rememberEntryResourceNavEntryDecorator
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class NavEntryResourcesUiTest {
    @Test
    fun `covered entry keeps its resource and popped entry gets a fresh one`() {
        runComposeUiTest {
            val stack = mutableStateListOf<NavKey>(Route(id = 1))
            val resources = mutableMapOf<Int, MutableList<Resource>>()
            val composed = mutableSetOf<Int>()
            setContent { TestHost(stack = stack, resources = resources, composed = composed) }
            onNodeWithText(text = "Screen 1").assertIsDisplayed()
            val first = runOnIdle { resources.getValue(key = 1).single() }

            runOnIdle { stack.add(element = Route(id = 2)) }
            waitForIdle()
            onNodeWithText(text = "Screen 2").assertIsDisplayed()
            val second = runOnIdle {
                assertFalse(actual = 1 in composed)
                assertEquals(expected = 0, actual = first.closes)
                resources.getValue(key = 2).single()
            }

            runOnIdle { stack.removeLast() }
            waitForIdle()
            onNodeWithText(text = "Screen 1").assertIsDisplayed()
            runOnIdle {
                assertEquals(expected = 1, actual = second.closes)
                assertEquals(expected = 0, actual = first.closes)
                assertSame(expected = first, actual = resources.getValue(key = 1).single())
                stack.add(element = Route(id = 2))
            }
            waitForIdle()
            runOnIdle {
                assertEquals(expected = 2, actual = resources.getValue(key = 2).size)
                assertNotSame(illegal = second, actual = resources.getValue(key = 2).last())
                assertEquals(expected = 0, actual = resources.getValue(key = 2).last().closes)
            }
        }
    }

    @Test
    fun `removing a covered entry closes only its resource`() {
        runComposeUiTest {
            val stack = mutableStateListOf<NavKey>(Route(id = 1))
            val resources = mutableMapOf<Int, MutableList<Resource>>()
            setContent { TestHost(stack = stack, resources = resources, composed = mutableSetOf()) }
            runOnIdle { stack.add(element = Route(id = 2)) }
            waitForIdle()
            runOnIdle { stack.removeFirst() }
            waitForIdle()
            onNodeWithText(text = "Screen 2").assertIsDisplayed()
            runOnIdle {
                assertEquals(expected = 1, actual = resources.getValue(key = 1).single().closes)
                assertEquals(expected = 0, actual = resources.getValue(key = 2).single().closes)
            }
        }
    }

    @Test
    fun `removing navigation host closes visible and covered resources`() {
        runComposeUiTest {
            val stack = mutableStateListOf<NavKey>(Route(id = 1))
            val resources = mutableMapOf<Int, MutableList<Resource>>()
            var showHost by mutableStateOf(value = true)
            setContent { if (showHost) TestHost(stack = stack, resources = resources, composed = mutableSetOf()) }
            runOnIdle { stack.add(element = Route(id = 2)) }
            waitForIdle()
            runOnIdle { showHost = false }
            waitForIdle()
            runOnIdle {
                assertEquals(expected = 1, actual = resources.getValue(key = 1).single().closes)
                assertEquals(expected = 1, actual = resources.getValue(key = 2).single().closes)
            }
        }
    }

    @Composable
    private fun TestHost(
        stack: List<NavKey>,
        resources: MutableMap<Int, MutableList<Resource>>,
        composed: MutableSet<Int>,
    ) {
        MaterialTheme {
            NavDisplay(
                backStack = stack,
                onBack = {},
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberEntryResourceNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<Route> { route ->
                        rememberNavEntryResource(key = Resource::class) {
                            Resource().also { resource ->
                                resources.getOrPut(key = route.id) { mutableListOf() }.add(element = resource)
                            }
                        }
                        DisposableEffect(key1 = route) {
                            composed.add(element = route.id)
                            onDispose { composed.remove(element = route.id) }
                        }
                        Text(text = "Screen ${route.id}")
                    }
                },
            )
        }
    }

    private data class Route(val id: Int) : NavKey

    private class Resource : AutoCloseable {
        var closes = 0

        override fun close() {
            closes++
        }
    }
}
