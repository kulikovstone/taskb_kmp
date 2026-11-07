package com.mkulikov.taskb.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigatorTest {
    @Test
    fun `start destination initializes an empty stack`() {
        val navigator = AppNavigator()
        navigator.setStartDestination(key = FirstRoute)
        assertEquals(expected = listOf<NavKey>(element = FirstRoute), actual = navigator.backStack.toList())
    }

    @Test
    fun `recomposition does not reset existing navigation`() {
        val navigator = AppNavigator()
        navigator.setStartDestination(key = FirstRoute)
        navigator.navigate(key = SecondRoute)
        navigator.setStartDestination(key = FirstRoute)
        assertEquals(expected = listOf<NavKey>(FirstRoute, SecondRoute), actual = navigator.backStack.toList())
    }

    @Test
    fun `replaceAll removes previous navigation history`() {
        val navigator = AppNavigator()
        navigator.setStartDestination(key = FirstRoute)
        navigator.navigate(key = SecondRoute)
        navigator.replaceAll(key = ThirdRoute)
        navigator.back()
        assertEquals(expected = listOf<NavKey>(element = ThirdRoute), actual = navigator.backStack.toList())
    }

    @Test
    fun `back returns to previous screen and keeps the root`() {
        val navigator = AppNavigator()
        navigator.setStartDestination(key = FirstRoute)
        navigator.navigate(key = SecondRoute)
        navigator.back()
        navigator.back()
        assertEquals(expected = listOf<NavKey>(element = FirstRoute), actual = navigator.backStack.toList())
    }

    @Test
    fun `replacing a detail keeps one back step to its list`() {
        val navigator = AppNavigator()
        navigator.setStartDestination(key = FirstRoute)
        navigator.navigate(key = SecondRoute)
        navigator.replaceTop(key = ThirdRoute)
        assertEquals(expected = listOf<NavKey>(FirstRoute, ThirdRoute), actual = navigator.backStack.toList())
        navigator.back()
        assertEquals(expected = listOf<NavKey>(element = FirstRoute), actual = navigator.backStack.toList())
    }

    @Test
    fun `popUpTo can close a list and its detail together`() {
        val navigator = AppNavigator()
        navigator.setStartDestination(key = FirstRoute)
        navigator.navigate(key = SecondRoute)
        navigator.navigate(key = ThirdRoute)
        navigator.popUpTo(key = SecondRoute)
        assertEquals(expected = listOf<NavKey>(FirstRoute, SecondRoute), actual = navigator.backStack.toList())
        navigator.navigate(key = ThirdRoute)
        navigator.popUpTo(key = SecondRoute, inclusive = true)
        assertEquals(expected = listOf<NavKey>(element = FirstRoute), actual = navigator.backStack.toList())
    }

    @Test
    fun `popUpTo ignores missing destinations and keeps the root`() {
        val navigator = AppNavigator()
        navigator.setStartDestination(key = FirstRoute)
        navigator.navigate(key = SecondRoute)
        navigator.popUpTo(key = ThirdRoute, inclusive = true)
        assertEquals(expected = listOf<NavKey>(FirstRoute, SecondRoute), actual = navigator.backStack.toList())
        navigator.popUpTo(key = FirstRoute, inclusive = true)
        assertEquals(expected = listOf<NavKey>(element = FirstRoute), actual = navigator.backStack.toList())
    }

    private data object FirstRoute : NavKey

    private data object SecondRoute : NavKey

    private data object ThirdRoute : NavKey
}
