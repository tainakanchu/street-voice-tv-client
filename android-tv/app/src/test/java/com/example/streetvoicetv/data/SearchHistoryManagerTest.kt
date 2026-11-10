package com.example.streetvoicetv.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SearchHistoryManagerTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `newest first, deduplicated and trimmed`() {
        val manager = SearchHistoryManager(context)
        manager.add("a")
        manager.add("b")
        manager.add(" a ")
        manager.add("   ")
        assertEquals(listOf("a", "b"), manager.history.value)
    }

    @Test
    fun `keeps at most 20 entries`() {
        val manager = SearchHistoryManager(context)
        repeat(25) { manager.add("q$it") }
        assertEquals(20, manager.history.value.size)
        assertEquals("q24", manager.history.value.first())
        assertEquals("q5", manager.history.value.last())
    }

    @Test
    fun `persists order and removals across instances`() {
        SearchHistoryManager(context).apply {
            add("周杰倫")
            add("米津玄師")
            add("lofi")
            remove("米津玄師")
        }
        assertEquals(listOf("lofi", "周杰倫"), SearchHistoryManager(context).history.value)
    }
}
