package com.mkulikov.taskb.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class NavEntryResourcesTest {
    @Test
    fun `resources belong to their entry and are closed exactly once`() {
        val owner = NavEntryResourceOwner()
        val first = owner.resourcesFor(contentKey = "first").getOrCreate(key = "store") { Resource() }
        val second = owner.resourcesFor(contentKey = "second").getOrCreate(key = "store") { Resource() }
        assertSame(
            expected = first,
            actual = owner.resourcesFor(contentKey = "first").getOrCreate(key = "store") { Resource() },
        )
        assertNotSame(illegal = first, actual = second)

        owner.remove(contentKey = "first")
        owner.remove(contentKey = "first")
        assertEquals(expected = 1, actual = first.closes)
        assertEquals(expected = 0, actual = second.closes)
        owner.close()
        owner.close()
        assertEquals(expected = 1, actual = first.closes)
        assertEquals(expected = 1, actual = second.closes)
    }

    @Test
    fun `disposing or abandoning the host closes its resources`() {
        listOf<(NavEntryResourceOwner) -> Unit>(
            { owner -> owner.onForgotten() },
            { owner -> owner.onAbandoned() },
        ).forEach { dispose ->
            val owner = NavEntryResourceOwner()
            val resource = owner.resourcesFor(contentKey = "entry").getOrCreate(key = "store") { Resource() }
            dispose(owner)
            assertEquals(expected = 1, actual = resource.closes)
            assertFailsWith<IllegalStateException> { owner.resourcesFor(contentKey = "entry") }
        }
    }

    @Test
    fun `failing cleanup does not prevent other resources from closing`() {
        val owner = NavEntryResourceOwner()
        val first = owner.resourcesFor(contentKey = "first").getOrCreate(key = "store") { Resource() }
        owner.resourcesFor(contentKey = "second").getOrCreate(key = "store") {
            AutoCloseable { error(message = "Cleanup failure") }
        }
        assertFailsWith<IllegalStateException> { owner.close() }
        assertEquals(expected = 1, actual = first.closes)
        owner.close()
        assertEquals(expected = 1, actual = first.closes)
    }

    private class Resource : AutoCloseable {
        var closes = 0

        override fun close() {
            closes++
        }
    }
}
