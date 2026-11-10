package com.example.streetvoicetv.playback

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.common.userMessageFor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * App-facing playback API (Hilt singleton): owns the queue, fetches signed HLS URLs and drives
 * the [PlaybackEngine]. The Media3 session around [sessionPlayer] lives in [PlaybackService],
 * which is started when playback starts so it keeps playing in the background.
 *
 * Loading model: switching song commits the queue position immediately, silences the old song,
 * cancels any in-flight URL fetch and tags the load with a generation number; results and player
 * events from older generations are ignored. [state] therefore always shows the song that is
 * (being) loaded, with [PlaybackState.isLoading] while its URL is fetched / buffered.
 */
@Singleton
class PlaybackManager internal constructor(
    private val engine: PlaybackEngine,
    private val settings: PlaybackSettings,
    private val fetchStream: suspend (songId: Int) -> Result<PlayableStream>,
    private val reportPlay: suspend (songId: Int) -> Unit,
    private val startService: () -> Unit,
    private val stopService: () -> Unit,
    dispatcher: CoroutineDispatcher,
    random: Random = Random.Default,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: StreetVoiceRepository,
        api: StreetVoiceApi,
        httpClient: dagger.Lazy<OkHttpClient>,
    ) : this(
        engine = ExoPlaybackEngine(context) { httpClient.get() },
        settings = SharedPreferencesPlaybackSettings(context),
        fetchStream = { repository.getStreamUrl(it) },
        reportPlay = { api.reportPlay(it) },
        startService = { PlaybackService.start(context) },
        stopService = { context.stopService(Intent(context, PlaybackService::class.java)) },
        dispatcher = Dispatchers.Main.immediate,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val queue = PlaybackQueue(random).apply {
        setShuffle(settings.shuffleEnabled)
        repeatMode = settings.repeatMode
    }

    private val _state = MutableStateFlow(
        PlaybackState(shuffleEnabled = queue.shuffleEnabled, repeatMode = queue.repeatMode)
    )

    /** Discrete playback state (does not tick with the position). */
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)

    /** Live playback position; ticks about once a second while playing. Collect only in progress UI. */
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    /** Song / playing / error only, for the app chrome (NowPlayingBar). */
    val nowPlaying: StateFlow<NowPlaying> = _state
        .map { NowPlaying(it.song, it.isPlaying, it.error) }
        .stateIn(scope, SharingStarted.Eagerly, NowPlaying())

    /** Player handed to the MediaSession: routes next / previous / play to the queue logic. */
    val sessionPlayer: Player by lazy { QueueForwardingPlayer(engine.asPlayer(), this) }

    private var generation = 0
    private var loadedGeneration = -1
    private var loading = false
    private var error: String? = null
    private var durationMs = 0L
    private var resumePositionMs = 0L
    private var retryCount = 0
    private var playReported = false
    private var loadJob: Job? = null
    private var tickerJob: Job? = null

    private val isLoaded: Boolean get() = loadedGeneration == generation

    init {
        engine.setListener(object : PlaybackEngine.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isLoaded) return
                if (isPlaying) {
                    retryCount = 0
                    reportPlayOnce()
                }
                updateTicker()
                publish()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean) = publish()

            override fun onPlaybackStateChanged(state: Int) {
                if (!isLoaded) return
                when (state) {
                    Player.STATE_READY -> durationMs = engine.duration
                    Player.STATE_ENDED -> onEnded()
                }
                publish()
            }

            override fun onError(error: PlaybackException) {
                if (!isLoaded) return
                stopTicker()
                val position = engine.currentPosition.takeIf { it > 0 } ?: _positionMs.value
                _positionMs.value = position
                handleFailure(
                    transient = error.isTransient(),
                    message = if (error.isNetworkError()) UserMessages.NETWORK else UserMessages.PLAYBACK_FAILED,
                    resumeAtMs = position,
                )
            }
        })
    }

    // ---------------------------------------------------------------------------------------
    // Queue
    // ---------------------------------------------------------------------------------------

    /**
     * Replaces the queue and plays [songs]`[startIndex]` (atomically: the old song stops at once).
     * If that song is already the current one and healthy, playback just continues.
     */
    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(songs.indices)
        val current = queue.current
        val continueCurrent = current != null && current.id == songs[index].id &&
            error == null && (loading || isLoaded)
        queue.replace(songs, index)
        if (continueCurrent) {
            queue.updateCurrent(current!!) // keep the richer (detail) object
            if (isLoaded && engine.playbackState == Player.STATE_ENDED) resume()
            publish()
        } else {
            retryCount = 0
            loadCurrent(startPositionMs = 0, newPlay = true)
        }
    }

    /** Same as [playQueue] (kept for callers that select a song from a list). */
    fun setQueue(songs: List<Song>, startIndex: Int) = playQueue(songs, startIndex)

    /** Replaces the current song with its full details (lyrics, synopsis...) if it is still current. */
    fun updateSongDetail(song: Song) {
        if (queue.updateCurrent(song)) publish()
    }

    fun playNext() {
        if (queue.moveToNext()) {
            retryCount = 0
            loadCurrent(startPositionMs = 0, newPlay = true)
        }
    }

    fun playPrevious() {
        if (error == null && isLoaded && _positionMs.value > RESTART_THRESHOLD_MS) {
            seekTo(0)
            return
        }
        if (queue.moveToPrevious()) {
            retryCount = 0
            loadCurrent(startPositionMs = 0, newPlay = true)
        } else {
            seekTo(0)
        }
    }

    fun toggleShuffle() {
        queue.setShuffle(!queue.shuffleEnabled)
        settings.shuffleEnabled = queue.shuffleEnabled
        publish()
    }

    fun toggleRepeatMode() {
        queue.repeatMode = when (queue.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        settings.repeatMode = queue.repeatMode
        publish()
    }

    // ---------------------------------------------------------------------------------------
    // Transport
    // ---------------------------------------------------------------------------------------

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else resume()
    }

    fun pause() {
        engine.pause()
        publish()
    }

    /**
     * Plays. After an error (or if the player was stopped) the stream URL is fetched again —
     * signed HLS URLs expire — and playback restarts at the last position. At the end of the
     * queue the current song restarts.
     */
    fun resume() {
        if (queue.current == null) return
        when {
            error != null -> {
                retryCount = 0
                loadCurrent(startPositionMs = resumePositionMs, newPlay = false)
            }
            loading -> engine.play()
            !isLoaded || engine.playbackState == Player.STATE_IDLE ->
                loadCurrent(startPositionMs = _positionMs.value, newPlay = false)
            engine.playbackState == Player.STATE_ENDED -> {
                playReported = false
                engine.seekTo(0)
                _positionMs.value = 0
                engine.play()
            }
            else -> engine.play()
        }
        publish()
    }

    /** Re-fetches the stream and resumes after an error. */
    fun retry() = resume()

    fun stop() {
        loadJob?.cancel()
        generation++
        loading = false
        error = null
        stopTicker()
        engine.clear()
        queue.clear()
        _positionMs.value = 0
        durationMs = 0
        publish()
        stopService()
    }

    fun seekTo(positionMs: Long) {
        val target = if (durationMs > 0) positionMs.coerceIn(0, durationMs) else positionMs.coerceAtLeast(0)
        resumePositionMs = target
        _positionMs.value = target
        if (isLoaded && error == null) engine.seekTo(target)
        publish()
    }

    /** Relative seek based on the latest target, so repeated presses accumulate (also while paused). */
    fun seekBy(deltaMs: Long) = seekTo(_positionMs.value + deltaMs)

    /** Handles TV remote media keys that did not reach the MediaSession. */
    fun handleMediaKey(keyCode: Int): Boolean {
        if (queue.current == null) return false
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> togglePlayPause()
            KeyEvent.KEYCODE_MEDIA_PLAY -> resume()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> pause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> playNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> playPrevious()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekBy(MEDIA_KEY_SEEK_MS)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seekBy(-MEDIA_KEY_SEEK_MS)
            else -> return false
        }
        return true
    }

    /** True while playback is wanted and running or about to (loading / retrying): keep the service in the foreground. */
    val isPlaybackActive: Boolean
        get() = queue.current != null && error == null && engine.playWhenReady &&
            (loading || (isLoaded && engine.playbackState.let { it == Player.STATE_READY || it == Player.STATE_BUFFERING }))

    val hasMedia: Boolean get() = queue.current != null

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    /**
     * Loads the current queue song: silences the previous one, cancels any stale load, fetches
     * the URL and starts it at [startPositionMs]. [newPlay] = a new song (play-count + auto play).
     */
    private fun loadCurrent(startPositionMs: Long, newPlay: Boolean) {
        val song = queue.current ?: return stop()
        loadJob?.cancel()
        val gen = ++generation
        loading = true
        error = null
        durationMs = 0
        resumePositionMs = startPositionMs
        if (newPlay) playReported = false
        stopTicker()
        _positionMs.value = startPositionMs
        engine.clear() // player events of the old media are now stale (generation changed)
        engine.play()
        publish()
        startService()
        loadJob = scope.launch {
            val result = fetchStream(song.id)
            if (gen != generation) return@launch
            result
                .onSuccess { stream ->
                    loading = false
                    loadedGeneration = gen
                    engine.load(stream.hlsUrl, song, startPositionMs)
                    publish()
                }
                .onFailure { e ->
                    if (e is CancellationException) return@onFailure
                    handleFailure(
                        transient = e is IOException,
                        message = userMessageFor(TAG, e, UserMessages.PLAYBACK_FAILED),
                        resumeAtMs = startPositionMs,
                    )
                }
        }
    }

    private fun handleFailure(transient: Boolean, message: String, resumeAtMs: Long) {
        resumePositionMs = resumeAtMs
        if (transient && retryCount < MAX_AUTO_RETRIES) {
            retryCount++
            val gen = ++generation
            loading = true
            error = null
            publish()
            loadJob?.cancel()
            loadJob = scope.launch {
                delay(retryDelayMs * retryCount)
                if (gen == generation) loadCurrent(startPositionMs = resumeAtMs, newPlay = false)
            }
        } else {
            loading = false
            error = message
            publish()
        }
    }

    private fun onEnded() {
        stopTicker()
        if (queue.repeatMode == RepeatMode.ONE) {
            playReported = false // each loop counts as a play
            _positionMs.value = 0
            engine.seekTo(0)
            engine.play()
        } else if (queue.moveToNext()) {
            loadCurrent(startPositionMs = 0, newPlay = true)
        } else {
            _positionMs.value = durationMs
        }
    }

    private fun reportPlayOnce() {
        if (playReported) return
        val songId = queue.current?.id ?: return
        playReported = true
        scope.launch {
            try {
                reportPlay(songId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun updateTicker() {
        if (!engine.isPlaying) {
            stopTicker()
            if (isLoaded) _positionMs.value = engine.currentPosition
            return
        }
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                val position = engine.currentPosition
                _positionMs.value = position
                // Align to the next whole second so the m:ss text changes on time.
                delay((1_000 - position % 1_000).coerceAtLeast(MIN_TICK_MS))
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun publish() {
        val song = queue.current
        val engineState = if (isLoaded) engine.playbackState else Player.STATE_IDLE
        val ended = engineState == Player.STATE_ENDED
        _state.value = PlaybackState(
            song = song,
            isPlaying = song != null && engine.playWhenReady && error == null && !ended,
            positionMs = _positionMs.value,
            durationMs = durationMs,
            error = if (song != null) error else null,
            queueSize = queue.size,
            queueIndex = queue.currentIndex,
            shuffleEnabled = queue.shuffleEnabled,
            repeatMode = queue.repeatMode,
            isLoading = song != null && error == null && (loading || engineState == Player.STATE_BUFFERING),
            nextAvailable = queue.hasNext,
            previousAvailable = queue.hasPrevious,
        )
    }

    companion object {
        private const val TAG = "PlaybackManager"
        const val RESTART_THRESHOLD_MS = 3_000L
        const val MAX_AUTO_RETRIES = 2
        private const val RETRY_DELAY_MS = 1_000L
        private const val MIN_TICK_MS = 50L
        private const val MEDIA_KEY_SEEK_MS = 10_000L
    }
}

private fun PlaybackException.isNetworkError(): Boolean = errorCode in
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED..PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

/** Errors worth an automatic retry with a fresh URL: network trouble or an expired / rejected signed URL. */
private fun PlaybackException.isTransient(): Boolean = isNetworkError() || errorCode in setOf(
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_TIMEOUT,
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
)
