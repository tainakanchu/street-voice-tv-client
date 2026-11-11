package com.example.streetvoicetv.tv

import android.content.Context
import android.os.SystemClock
import com.example.streetvoicetv.playback.PlaybackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the Watch Next program off the main thread, throttled: an [requestUpdate] is written
 * after [settleMs] (so transient states, e.g. stop() clearing the queue just before the saved
 * session appears, are skipped) and at most once per [minIntervalMs]. [flush] writes at once
 * (app going to the background). The target is read from [target] when the write happens, so
 * it carries the latest position; null = leave the row alone. Identical targets are not rewritten.
 */
class WatchNextPublisher(
    private val store: WatchNextStore,
    private val scope: CoroutineScope,
    private val target: () -> WatchNextTarget?,
    private val clock: () -> Long,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val settleMs: Long = SETTLE_MS,
) {
    private val mutex = Mutex()
    private var pending: Job? = null
    private var written: WatchNextTarget? = null
    private var lastWriteAt: Long? = null

    /** Schedules a (throttled) write unless one is already pending. Call from one thread. */
    fun requestUpdate() {
        if (pending?.isActive == true) return
        pending = scope.launch {
            val sinceLast = lastWriteAt?.let { clock() - it }
            val wait = if (sinceLast == null) settleMs else maxOf(settleMs, minIntervalMs - sinceLast)
            delay(wait)
            write()
        }
    }

    /** Writes the current target now (replacing a pending write). Call from one thread. */
    fun flush() {
        pending?.cancel()
        pending = scope.launch { write() }
    }

    private suspend fun write() = mutex.withLock {
        val next = target() ?: return@withLock
        if (next == written) return@withLock
        when (next) {
            is WatchNextTarget.Publish -> store.publish(next.spec)
            WatchNextTarget.Remove -> store.remove()
        }
        written = next
        lastWriteAt = clock()
    }

    companion object {
        const val MIN_INTERVAL_MS = 15_000L
        const val SETTLE_MS = 1_500L
    }
}

/**
 * Keeps the Google TV 「繼續觀看」 row in sync with playback: updated on song change, pause and
 * when the app goes to the background ([onAppStopped]); removed when there is neither a song
 * nor a saved session any more. Does nothing on non-TV devices. Started by [start] (idempotent).
 */
@Singleton
class WatchNextSync internal constructor(
    private val playbackManager: PlaybackManager,
    store: WatchNextStore,
    private val enabled: Boolean,
    mainDispatcher: CoroutineDispatcher,
    ioDispatcher: CoroutineDispatcher,
    private val wallClock: () -> Long = System::currentTimeMillis,
    elapsedClock: () -> Long = SystemClock::elapsedRealtime,
) {
    @Inject
    constructor(@ApplicationContext context: Context, playbackManager: PlaybackManager) : this(
        playbackManager = playbackManager,
        store = TvProviderWatchNextStore(context),
        enabled = isTvDevice(context),
        mainDispatcher = Dispatchers.Main.immediate,
        ioDispatcher = Dispatchers.IO.limitedParallelism(1),
    )

    private val mainScope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var started = false

    /** Removal only after we published something in this process (the saved session loads late). */
    @Volatile private var seenProgram = false

    private val publisher = WatchNextPublisher(
        store = store,
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        target = ::currentTarget,
        clock = elapsedClock,
    )

    fun start() {
        if (started || !enabled) return
        started = true
        mainScope.launch {
            combine(
                playbackManager.state.map { it.song?.id to it.isPlaying }.distinctUntilChanged(),
                playbackManager.restorable.map { it?.currentSong?.id }.distinctUntilChanged(),
            ) { _, _ -> Unit }.collect { publisher.requestUpdate() }
        }
    }

    /** The app went to the background: write the current position now. */
    fun onAppStopped() {
        if (started) publisher.flush()
    }

    private fun currentTarget(): WatchNextTarget? {
        val state = playbackManager.state.value
        val target = WatchNextMapping.targetOf(
            song = state.song,
            positionMs = playbackManager.positionMs.value,
            durationMs = state.durationMs,
            restorable = playbackManager.restorable.value,
            nowMs = wallClock(),
        )
        if (target is WatchNextTarget.Publish) seenProgram = true
        return if (target == WatchNextTarget.Remove && !seenProgram) null else target
    }
}
