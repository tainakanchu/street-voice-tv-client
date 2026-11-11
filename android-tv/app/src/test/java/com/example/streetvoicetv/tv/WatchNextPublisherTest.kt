package com.example.streetvoicetv.tv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchNextPublisherTest {

    private class FakeStore : WatchNextStore {
        val calls = mutableListOf<String>()
        override fun publish(spec: WatchNextSpec) {
            calls += "publish:${spec.songId}@${spec.positionMs}"
        }
        override fun remove() {
            calls += "remove"
        }
    }

    private val scope = TestScope()
    private val store = FakeStore()
    private var target: WatchNextTarget? = null
    private val publisher = WatchNextPublisher(
        store = store,
        scope = scope,
        target = { target },
        clock = { scope.testScheduler.currentTime },
        minIntervalMs = 10_000,
        settleMs = 1_000,
    )

    private fun publish(songId: Int, positionMs: Long = 0) {
        target = WatchNextTarget.Publish(WatchNextSpec(songId, "t", "a", null, positionMs, 100_000, 0))
    }

    private fun advance(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    @Test
    fun `writes after the settle delay with the target read at write time`() {
        publish(1)
        publisher.requestUpdate()
        advance(500)
        assertEquals(emptyList<String>(), store.calls)
        publish(1, positionMs = 3_000) // e.g. the position caught up after a pause
        advance(600)
        assertEquals(listOf("publish:1@3000"), store.calls)
    }

    @Test
    fun `transient states inside the settle delay are never written`() {
        publish(1)
        publisher.requestUpdate()
        target = WatchNextTarget.Remove // stop(): queue cleared, session not set yet
        publisher.requestUpdate()
        publish(1, positionMs = 9_000) // the saved session appears
        publisher.requestUpdate()
        advance(2_000)
        assertEquals(listOf("publish:1@9000"), store.calls)
    }

    @Test
    fun `writes at most once per interval, the latest target last`() {
        publish(1)
        publisher.requestUpdate()
        advance(1_000)
        publish(2)
        publisher.requestUpdate()
        publish(3)
        publisher.requestUpdate()
        advance(5_000)
        assertEquals(listOf("publish:1@0"), store.calls)
        advance(5_000)
        assertEquals(listOf("publish:1@0", "publish:3@0"), store.calls)
    }

    @Test
    fun `identical targets are not rewritten and null leaves the row alone`() {
        publish(1)
        publisher.requestUpdate()
        advance(1_000)
        publisher.requestUpdate()
        advance(20_000)
        target = null
        publisher.requestUpdate()
        advance(20_000)
        assertEquals(listOf("publish:1@0"), store.calls)
    }

    @Test
    fun `flush writes at once, replacing the pending write`() {
        publish(1)
        publisher.requestUpdate()
        advance(1_000)
        publish(1, positionMs = 50_000)
        publisher.requestUpdate()
        publisher.flush() // app stopped
        scope.runCurrent()
        assertEquals(listOf("publish:1@0", "publish:1@50000"), store.calls)
        advance(30_000)
        assertEquals("the cancelled throttled write does not repeat it", 2, store.calls.size)
    }

    @Test
    fun `removal is written`() {
        publish(1)
        publisher.requestUpdate()
        advance(1_000)
        target = WatchNextTarget.Remove
        publisher.requestUpdate()
        advance(10_000)
        assertEquals(listOf("publish:1@0", "remove"), store.calls)
    }
}
