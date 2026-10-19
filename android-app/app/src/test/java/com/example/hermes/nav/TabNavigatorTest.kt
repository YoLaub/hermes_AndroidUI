package com.example.hermes.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabNavigatorTest {
    private val start = TabNavigator.start { tab -> "root-${tab.name}" }

    @Test
    fun `starts on Chat with every tab at its root`() {
        assertEquals(Tab.Chat, start.current)
        Tab.entries.forEach { assertEquals(listOf("root-${it.name}"), start.stackOf(it)) }
    }

    @Test
    fun `selecting a tab keeps the stack of the others`() {
        val nav = start.select(Tab.Space).push("kanban").select(Tab.Agent)
        assertEquals(Tab.Agent, nav.current)
        assertEquals(listOf("root-Space", "kanban"), nav.stackOf(Tab.Space))
    }

    @Test
    fun `re-tapping the current tab returns to its root`() {
        val nav = start.select(Tab.Space).push("kanban").push("detail").select(Tab.Space)
        assertEquals(listOf("root-Space"), nav.stackOf(Tab.Space))
    }

    @Test
    fun `back pops the current tab first`() {
        val nav = start.select(Tab.Space).push("kanban").back()!!
        assertEquals(Tab.Space, nav.current)
        assertEquals(listOf("root-Space"), nav.stackOf(Tab.Space))
    }

    @Test
    fun `back at the root of another tab returns to Chat`() {
        val nav = start.select(Tab.Control).back()!!
        assertEquals(Tab.Chat, nav.current)
    }

    @Test
    fun `back at the root of Chat leaves the app`() {
        assertNull(start.back())
    }

    @Test
    fun `navigating to a tab can push a screen on it`() {
        val nav = start.open(Tab.Space, "kanban")
        assertEquals(Tab.Space, nav.current)
        assertEquals(listOf("root-Space", "kanban"), nav.stackOf(Tab.Space))
    }
}
