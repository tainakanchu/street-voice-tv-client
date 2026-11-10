package com.example.streetvoicetv.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.random.Random

fun testSong(id: Int) = Song(
    id = id,
    name = "Song $id",
    artistName = "Artist",
    artistUsername = "artist",
    imageUrl = null,
    durationSeconds = 200.0,
    playsCount = 0,
)

fun testSongs(count: Int, firstId: Int = 1) = List(count) { testSong(firstId + it) }

/**
 * Minimal ExoPlayer stand-in: state changes only when the test (or the manager) drives it.
 * Like ExoPlayer, getters are updated before listeners run and events raised from inside a
 * listener are queued after the ones being delivered (in order).
 */
class FakePlaybackEngine : PlaybackEngine {
    private var listener: PlaybackEngine.Listener? = null
    private val events = ArrayDeque<(PlaybackEngine.Listener) -> Unit>()
    private var flushing = false

    override var isPlaying = false
        private set
    override var playWhenReady = false
        private set
    override var playbackState = Player.STATE_IDLE
        private set
    override var currentPosition = 0L
    override var duration = 0L
        private set

    /** (url, startPositionMs) of every load. */
    val loads = mutableListOf<Pair<String, Long>>()

    override fun setListener(listener: PlaybackEngine.Listener) {
        this.listener = listener
    }

    override fun load(url: String, song: Song, startPositionMs: Long) {
        loads += url to startPositionMs
        currentPosition = startPositionMs
        setState(Player.STATE_BUFFERING)
    }

    override fun play() = setPlayWhenReady(true)
    override fun pause() = setPlayWhenReady(false)

    override fun clear() {
        duration = 0
        currentPosition = 0
        setState(Player.STATE_IDLE)
    }

    override fun seekTo(positionMs: Long) {
        currentPosition = positionMs
        if (playbackState == Player.STATE_ENDED) setState(Player.STATE_READY)
    }

    override fun release() = Unit
    override fun asPlayer(): Player = throw UnsupportedOperationException()

    // --- test controls ---

    fun becomeReady(durationMs: Long = 200_000) {
        duration = durationMs
        setState(Player.STATE_READY)
    }

    fun end() {
        currentPosition = duration
        setState(Player.STATE_ENDED)
    }

    fun fail(errorCode: Int) {
        setState(Player.STATE_IDLE)
        emit { it.onError(PlaybackException("boom", null, errorCode)) }
    }

    /** Delivers a raw ENDED event without changing the engine (simulates a late, stale event). */
    fun deliverStaleEnded() = emit { it.onPlaybackStateChanged(Player.STATE_ENDED) }

    private fun setPlayWhenReady(value: Boolean) {
        if (playWhenReady == value) return
        playWhenReady = value
        updatePlaying { it.onPlayWhenReadyChanged(value) }
    }

    private fun setState(state: Int) {
        if (playbackState == state) return
        playbackState = state
        updatePlaying { it.onPlaybackStateChanged(state) }
    }

    /** Updates isPlaying, then emits [event] followed by the isPlaying change (if any). */
    private fun updatePlaying(event: (PlaybackEngine.Listener) -> Unit) {
        val playing = playWhenReady && playbackState == Player.STATE_READY
        val changed = playing != isPlaying
        isPlaying = playing
        emit(event)
        if (changed) emit { it.onIsPlayingChanged(playing) }
    }

    private fun emit(event: (PlaybackEngine.Listener) -> Unit) {
        events.addLast(event)
        if (flushing) return
        flushing = true
        try {
            while (events.isNotEmpty()) {
                val next = events.removeFirst()
                listener?.let(next)
            }
        } finally {
            flushing = false
        }
    }
}

/** Stream URL source. By default answers at once; with [gated] each request waits for [complete]. */
class FakeStreams {
    val requests = mutableListOf<Int>()
    var gated = false
    private val pending = mutableMapOf<Int, MutableList<CompletableDeferred<Result<PlayableStream>>>>()

    /** Song ids whose URL fetch fails (non-network error). */
    val failing = mutableSetOf<Int>()

    suspend fun fetch(songId: Int): Result<PlayableStream> {
        requests += songId
        if (!gated) return resultFor(songId)
        val deferred = CompletableDeferred<Result<PlayableStream>>()
        pending.getOrPut(songId) { mutableListOf() } += deferred
        return deferred.await()
    }

    fun complete(songId: Int) {
        pending[songId]?.removeFirstOrNull()?.complete(resultFor(songId))
    }

    private fun resultFor(songId: Int): Result<PlayableStream> =
        if (songId in failing) {
            Result.failure(IllegalStateException("no stream"))
        } else {
            Result.success(PlayableStream(songId, url(songId)))
        }

    companion object {
        fun url(songId: Int) = "https://example.invalid/$songId.m3u8"
    }
}

class InMemorySettings(
    override var shuffleEnabled: Boolean = false,
    override var repeatMode: RepeatMode = RepeatMode.OFF,
) : PlaybackSettings

/** A [PlaybackManager] wired to fakes. */
class ManagerHarness(
    dispatcher: CoroutineDispatcher,
    settings: PlaybackSettings = InMemorySettings(),
    seed: Int = 42,
) {
    val engine = FakePlaybackEngine()
    val streams = FakeStreams()
    val reported = mutableListOf<Int>()
    var serviceStarts = 0
    var serviceStops = 0

    val manager = PlaybackManager(
        engine = engine,
        settings = settings,
        fetchStream = { streams.fetch(it) },
        reportPlay = { reported += it },
        startService = { serviceStarts++ },
        stopService = { serviceStops++ },
        dispatcher = dispatcher,
        random = Random(seed),
        retryDelayMs = 1_000,
    )
}
