package com.example.streetvoicetv.playback

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.data.local.JsonFilePlaybackStore
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
import kotlinx.coroutines.channels.Channel
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
 *
 * Besides transport and the queue it owns:
 * - **Session restore** ([restorable], [resumeLastSession]) and **recently played**
 *   ([recentlyPlayed]), persisted through a [PlaybackStore] on `ioDispatcher` — saved on song
 *   change, pause, queue edits, every 15 s while playing and on [saveSessionNow] (activity stop).
 * - **自動播放** ([autoplayEnabled]): when the queue is about to run out (repeat off), songs from
 *   a [RadioSource] are appended (flagged [QueueItem.isAutoplay]) and playback continues.
 * - **Queue edits** for context menus: [playNext] (song), [addToQueue], [removeFromQueue].
 * - **Sleep timer** ([sleepTimer], [setSleepTimer]): fades out over the last 10 s, then pauses.
 *   It lives here, so it keeps running when the player screen is left.
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
    private val store: PlaybackStore = PlaybackStore.None,
    private val radioSource: RadioSource = RadioSource { _, _, _ -> emptyList() },
    /** Where persistence runs (Dispatchers.IO in production). */
    private val ioDispatcher: CoroutineDispatcher = dispatcher,
    /** Epoch ms (timestamps of saved sessions / recently played). */
    private val wallClock: () -> Long = System::currentTimeMillis,
    /** Monotonic ms (sleep timer). */
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
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
        store = JsonFilePlaybackStore({ context.filesDir }),
        radioSource = MixedRadioSource.default(repository),
        ioDispatcher = Dispatchers.IO,
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

    private val _autoplayEnabled = MutableStateFlow(settings.autoplayEnabled)

    /** 自動播放 setting (persisted, default on). Toggle with [setAutoplayEnabled] / [toggleAutoplay]. */
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    private val _upNext = MutableStateFlow(queue.view(_autoplayEnabled.value))
    private var upNextRevision = queue.revision
    private var upNextAutoplay = _autoplayEnabled.value

    private val _restorable = MutableStateFlow<RestorableSession?>(null)

    /**
     * The last saved session (queue with song metadata, current index, shuffle order, position)
     * while nothing is loaded; null while a queue is active or when nothing was saved.
     * Loaded from storage asynchronously after creation, so it may turn non-null shortly after
     * app start. Use it for a 「繼續播放」 entry: [RestorableSession.currentSong] for the title /
     * art, [RestorableSession.positionMs] for the progress. Resume with [resumeLastSession].
     */
    val restorable: StateFlow<RestorableSession?> = _restorable.asStateFlow()

    private val _recentEntries = MutableStateFlow<List<RecentlyPlayedEntry>>(emptyList())
    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    private var recentLoaded = false

    /**
     * The last [MAX_RECENTLY_PLAYED] distinct songs that started playing, most recent first
     * (a song played again moves to the front). Persisted locally (the server has no history
     * API) and loaded asynchronously after creation. Songs carry list metadata only (no lyrics).
     */
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    /** [recentlyPlayed] with the time each song last started playing (epoch ms). */
    val recentlyPlayedEntries: StateFlow<List<RecentlyPlayedEntry>> = _recentEntries.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)

    /**
     * The sleep timer. A [SleepTimerState.Countdown] ticks about once a second, so collect it only
     * where the remaining time is drawn.
     */
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    /**
     * The queue in play order (shuffle / repeat applied) with the current position. Changes only
     * when the queue, the current song or the shuffle / repeat mode changes — never on position
     * ticks or play / pause.
     */
    val upNext: StateFlow<QueueView> = _upNext.asStateFlow()

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
    private var saveTickerJob: Job? = null

    // Autoplay
    private var autoplayJob: Job? = null
    private var autoplayInFlight = false
    private var advanceOnAutoplay = false

    /** Id of the seed song for which the radio found nothing (no retry until the song changes). */
    private var autoplayExhaustedFor: Int? = null

    // Sleep timer
    private var sleepJob: Job? = null
    private var sleepAtEndOfSong = false

    /** Volume before the sleep fade started (restored after pausing / cancelling). */
    private var fadeBaseVolume: Float? = null

    private val sessionWriter = ConflatedWriter<RestorableSession>(scope, ioDispatcher) { store.saveSession(it) }
    private val recentWriter = ConflatedWriter<List<RecentlyPlayedEntry>>(scope, ioDispatcher) {
        store.saveRecentlyPlayed(it)
    }

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
        scope.launch {
            val saved = try {
                store.loadSession()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (queue.current == null && _restorable.value == null) _restorable.value = saved
        }
        scope.launch {
            val saved = try {
                store.loadRecentlyPlayed()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            // Songs played since start-up stay in front of the loaded history; they were not
            // written yet (that would have replaced the history before it was read).
            val playedMeanwhile = _recentEntries.value.isNotEmpty()
            recentLoaded = true
            setRecent(
                (_recentEntries.value + saved).distinctBy { it.song.id }.take(MAX_RECENTLY_PLAYED),
                save = playedMeanwhile,
            )
        }
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
        resetAutoplay()
        queue.replace(songs, index)
        if (continueCurrent) {
            queue.updateCurrent(current) // keep the richer (detail) object
            if (isLoaded && engine.playbackState == Player.STATE_ENDED) resume()
            publish()
            persistSession()
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

    /** Skips to the next song; at the end of the queue (repeat off) 自動播放 fetches more first. */
    fun playNext() {
        if (queue.moveToNext()) {
            retryCount = 0
            loadCurrent(startPositionMs = 0, newPlay = true)
        } else if (autoplayCanExtend) {
            requestAutoplay(advance = true)
        }
    }

    /**
     * Inserts [song] to play right after the current one (in play order, also with shuffle on).
     * With nothing queued it starts playing it.
     */
    fun playNext(song: Song) {
        if (queue.current == null) return playQueue(listOf(song), 0)
        queue.insertNext(song)
        publish()
        persistSession()
    }

    /**
     * Adds [song] after the songs the user queued (before any autoplay songs).
     * With nothing queued it starts playing it.
     */
    fun addToQueue(song: Song) {
        if (queue.current == null) return playQueue(listOf(song), 0)
        queue.insertIntoQueue(song)
        publish()
        persistSession()
    }

    /**
     * Removes the queue song at [queueIndex] (a [QueueItem.queueIndex]). Removing the current
     * song skips to the next one (keeping play / pause); if nothing follows, the previous song
     * is loaded paused; removing the only song stops playback.
     */
    fun removeFromQueue(queueIndex: Int) {
        val wasPlaying = engine.playWhenReady
        when (queue.remove(queueIndex)) {
            PlaybackQueue.Removal.NONE -> return
            PlaybackQueue.Removal.REMOVED -> publish()
            PlaybackQueue.Removal.CURRENT_SKIPPED -> {
                retryCount = 0
                loadCurrent(startPositionMs = 0, newPlay = true, playWhenReady = wasPlaying)
            }
            PlaybackQueue.Removal.CURRENT_REMOVED_AT_END -> {
                retryCount = 0
                loadCurrent(startPositionMs = 0, newPlay = true, playWhenReady = false)
            }
            PlaybackQueue.Removal.EMPTIED -> return stop()
        }
        persistSession()
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

    /**
     * Jumps to the queue song at [queueIndex] (a [QueueItem.queueIndex]) without rebuilding the
     * queue (the shuffled order is kept). Selecting the current song resumes it if it is paused,
     * ended or failed instead of restarting it.
     */
    fun playQueueItem(queueIndex: Int) {
        if (queueIndex !in 0 until queue.size) return
        if (queueIndex == queue.currentIndex) {
            if (!_state.value.isPlaying) resume()
            return
        }
        if (queue.jumpTo(queueIndex)) {
            retryCount = 0
            loadCurrent(startPositionMs = 0, newPlay = true)
        }
    }

    fun toggleShuffle() {
        queue.setShuffle(!queue.shuffleEnabled)
        settings.shuffleEnabled = queue.shuffleEnabled
        publish()
        persistSession()
    }

    fun toggleAutoplay() = setAutoplayEnabled(!_autoplayEnabled.value)

    /** Turning 自動播放 off also removes the autoplay songs that have not played yet. */
    fun setAutoplayEnabled(enabled: Boolean) {
        if (_autoplayEnabled.value == enabled) return
        settings.autoplayEnabled = enabled
        _autoplayEnabled.value = enabled
        if (!enabled) {
            cancelAutoplayFetch()
            queue.dropUpcomingAutoplay()
        }
        autoplayExhaustedFor = null
        publish()
        persistSession()
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
        persistSession()
    }

    /**
     * Plays. After an error (or if the player was stopped) the stream URL is fetched again —
     * signed HLS URLs expire — and playback restarts at the last position. At the end of the
     * queue the current song restarts.
     */
    fun resume() {
        if (queue.current == null) return
        startService() // may have been loaded paused (session restore / sleep timer)
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
        // Keep the session restorable (e.g. the task was removed while paused).
        val session = sessionSnapshot()
        session?.let(sessionWriter::submit)
        cancelSleepTimer()
        resetAutoplay()
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
        _restorable.value = session
        stopService()
    }

    // ---------------------------------------------------------------------------------------
    // Session restore
    // ---------------------------------------------------------------------------------------

    /**
     * Restores [restorable]: rebuilds the queue (songs, current index, shuffle order, autoplay
     * songs), re-fetches the stream URL (signed URLs expire) and starts at the saved position.
     * Nothing calls this automatically — the app never starts playing by itself on start-up;
     * call it from a 「繼續播放」 action. [play] = false only prepares it, paused.
     * Returns false when there is nothing to restore.
     */
    fun resumeLastSession(play: Boolean = true): Boolean {
        val session = _restorable.value ?: return false
        resetAutoplay()
        queue.restore(session.queue)
        settings.shuffleEnabled = queue.shuffleEnabled
        _restorable.value = null
        retryCount = 0
        val songMs = (session.currentSong.durationSeconds * 1000).toLong()
        // Saved at the very end: start the song over instead of ending at once.
        val position = if (songMs > 0 && session.positionMs >= songMs - END_SLACK_MS) 0L else session.positionMs
        loadCurrent(startPositionMs = position, newPlay = true, playWhenReady = play)
        return true
    }

    /** Saves the session now (throttling aside); call when the app goes to the background. */
    fun saveSessionNow() = persistSession()

    // ---------------------------------------------------------------------------------------
    // Sleep timer
    // ---------------------------------------------------------------------------------------

    /** Starts / replaces / cancels ([SleepTimerOption.OFF]) the sleep timer. */
    fun setSleepTimer(option: SleepTimerOption) {
        cancelSleepTimer()
        when (option) {
            SleepTimerOption.OFF -> Unit
            SleepTimerOption.END_OF_SONG -> {
                sleepAtEndOfSong = true
                _sleepTimer.value = SleepTimerState.EndOfSong
                fadeTowardsEndOfSong(_positionMs.value)
            }
            else -> startCountdown(option.minutes!! * 60_000L)
        }
    }

    private fun startCountdown(durationMs: Long) {
        val deadline = elapsedClock() + durationMs
        _sleepTimer.value = SleepTimerState.Countdown(durationMs)
        sleepJob = scope.launch {
            while (true) {
                val remaining = deadline - elapsedClock()
                if (remaining <= 0) break
                _sleepTimer.value = SleepTimerState.Countdown(remaining)
                if (remaining <= SLEEP_FADE_MS) {
                    fadeTo(remaining.toFloat() / SLEEP_FADE_MS)
                    delay(minOf(FADE_STEP_MS, remaining))
                } else {
                    delay(minOf(1_000L, remaining - SLEEP_FADE_MS))
                }
            }
            sleepJob = null
            fallAsleep()
        }
    }

    /** Pauses for the sleep timer and puts the volume back. */
    private fun fallAsleep() {
        sleepAtEndOfSong = false
        _sleepTimer.value = SleepTimerState.Off
        pause()
        restoreVolume()
    }

    private fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepAtEndOfSong = false
        restoreVolume()
        _sleepTimer.value = SleepTimerState.Off
    }

    private fun fadeTo(fraction: Float) {
        val base = fadeBaseVolume ?: engine.volume.also { fadeBaseVolume = it }
        engine.volume = base * fraction.coerceIn(0f, 1f)
    }

    private fun restoreVolume() {
        fadeBaseVolume?.let { engine.volume = it }
        fadeBaseVolume = null
    }

    /** 「本曲結束」: fades over the last 10 s of the song (called from the position ticker). */
    private fun fadeTowardsEndOfSong(positionMs: Long) {
        if (!sleepAtEndOfSong || durationMs <= 0) return
        val remaining = durationMs - positionMs
        if (remaining <= SLEEP_FADE_MS) fadeTo(remaining.toFloat() / SLEEP_FADE_MS) else restoreVolume()
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
    private fun loadCurrent(startPositionMs: Long, newPlay: Boolean, playWhenReady: Boolean = true) {
        val song = queue.current ?: return stop()
        if (sleepAtEndOfSong) restoreVolume() // skipped away from a fading last song
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
        if (playWhenReady) engine.play() else engine.pause()
        publish()
        if (playWhenReady) startService()
        if (newPlay) persistSession()
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
        if (sleepAtEndOfSong) {
            // Sleep timer 「本曲結束」: stop here; the next song waits (loaded paused).
            fallAsleep()
            if (queue.moveToNext()) {
                loadCurrent(startPositionMs = 0, newPlay = true, playWhenReady = false)
            } else {
                _positionMs.value = durationMs
            }
            return
        }
        if (queue.repeatMode == RepeatMode.ONE) {
            playReported = false // each loop counts as a play
            _positionMs.value = 0
            engine.seekTo(0)
            engine.play()
        } else if (queue.moveToNext()) {
            loadCurrent(startPositionMs = 0, newPlay = true)
        } else {
            _positionMs.value = durationMs
            if (autoplayCanExtend) requestAutoplay(advance = true)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Autoplay
    // ---------------------------------------------------------------------------------------

    private val autoplayActive: Boolean
        get() = _autoplayEnabled.value && queue.repeatMode == RepeatMode.OFF && queue.current != null

    /** Autoplay may still add songs after the current one (for [PlaybackState.hasNext]). */
    private val autoplayCanExtend: Boolean
        get() = autoplayActive && (autoplayInFlight || autoplayExhaustedFor != queue.current?.id)

    /** Fetches more songs ahead of time once at most [AUTOPLAY_PREFETCH_REMAINING] songs remain. */
    private fun maybePrefetchAutoplay() {
        if (autoplayInFlight || !autoplayCanExtend) return
        if (queue.upcomingCount + 1 <= AUTOPLAY_PREFETCH_REMAINING) requestAutoplay(advance = false)
    }

    /** Asks the [radioSource] for songs to append; with [advance], moves on to the first one. */
    private fun requestAutoplay(advance: Boolean) {
        if (advance) advanceOnAutoplay = true
        if (autoplayInFlight) return
        val seed = queue.current ?: return
        autoplayInFlight = true
        val exclude = queue.songs.mapTo(HashSet()) { it.id } + _recentEntries.value.map { it.song.id }
        autoplayJob = scope.launch {
            val found = try {
                radioSource.nextSongs(seed, exclude, AUTOPLAY_BATCH)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            autoplayInFlight = false
            autoplayJob = null
            val advanceNow = advanceOnAutoplay
            advanceOnAutoplay = false
            if (!autoplayActive) return@launch publish()
            val stillQueued = queue.songs.mapTo(HashSet()) { it.id }
            val fresh = found.filter { it.id !in exclude && it.id !in stillQueued }.distinctBy { it.id }
            if (fresh.isEmpty()) {
                autoplayExhaustedFor = seed.id
                return@launch publish()
            }
            queue.appendAutoplay(fresh)
            // Continue only if playback is still waiting at the end (no skip happened meanwhile).
            if (advanceNow && queue.current?.id == seed.id && queue.moveToNext()) {
                retryCount = 0
                loadCurrent(startPositionMs = 0, newPlay = true)
            } else {
                publish()
                persistSession()
            }
        }
    }

    private fun cancelAutoplayFetch() {
        autoplayJob?.cancel()
        autoplayJob = null
        autoplayInFlight = false
        advanceOnAutoplay = false
    }

    private fun resetAutoplay() {
        cancelAutoplayFetch()
        autoplayExhaustedFor = null
    }

    // ---------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------

    private fun sessionSnapshot(): RestorableSession? {
        val snapshot = queue.snapshot() ?: return null
        val position = if (isLoaded && engine.isPlaying) engine.currentPosition else _positionMs.value
        return RestorableSession(snapshot, position, wallClock())
    }

    private fun persistSession() {
        sessionSnapshot()?.let(sessionWriter::submit)
    }

    private fun recordPlayed(song: Song) {
        val entry = RecentlyPlayedEntry(song, wallClock())
        setRecent(
            (listOf(entry) + _recentEntries.value.filter { it.song.id != song.id }).take(MAX_RECENTLY_PLAYED),
            save = true,
        )
    }

    private fun setRecent(entries: List<RecentlyPlayedEntry>, save: Boolean) {
        _recentEntries.value = entries
        _recentlyPlayed.value = entries.map { it.song }
        if (save && recentLoaded) recentWriter.submit(entries)
    }

    private fun reportPlayOnce() {
        if (playReported) return
        val song = queue.current ?: return
        val songId = song.id
        playReported = true
        recordPlayed(song)
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
                fadeTowardsEndOfSong(position)
                // Align to the next whole second so the m:ss text changes on time.
                delay((1_000 - position % 1_000).coerceAtLeast(MIN_TICK_MS))
            }
        }
        if (saveTickerJob?.isActive != true) {
            saveTickerJob = scope.launch {
                while (true) {
                    delay(SAVE_INTERVAL_MS)
                    persistSession()
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        saveTickerJob?.cancel()
        saveTickerJob = null
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
            nextAvailable = queue.hasNext || autoplayCanExtend,
            previousAvailable = queue.hasPrevious,
        )
        val autoplay = _autoplayEnabled.value
        if (queue.revision != upNextRevision || autoplay != upNextAutoplay) {
            upNextRevision = queue.revision
            upNextAutoplay = autoplay
            _upNext.value = queue.view(autoplay)
        }
        if (song != null) {
            _restorable.value = null
            maybePrefetchAutoplay()
        }
    }

    companion object {
        private const val TAG = "PlaybackManager"
        const val RESTART_THRESHOLD_MS = 3_000L
        const val MAX_AUTO_RETRIES = 2
        private const val RETRY_DELAY_MS = 1_000L
        private const val MIN_TICK_MS = 50L
        private const val MEDIA_KEY_SEEK_MS = 10_000L

        /** Save the session this often while playing. */
        const val SAVE_INTERVAL_MS = 15_000L

        /** Size of the recently played list. */
        const val MAX_RECENTLY_PLAYED = 50

        /** Songs appended per autoplay fetch. */
        const val AUTOPLAY_BATCH = 10

        /** Autoplay fetches ahead once this many songs (current included) remain. */
        const val AUTOPLAY_PREFETCH_REMAINING = 2

        /** The sleep timer fades the volume out over this last part. */
        const val SLEEP_FADE_MS = 10_000L
        private const val FADE_STEP_MS = 250L
        private const val END_SLACK_MS = 3_000L
    }
}

/**
 * Hands the latest value to [write] on [dispatcher], one write at a time; values submitted
 * while a write runs are conflated (only the newest is written). Failures are ignored.
 */
private class ConflatedWriter<T : Any>(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val write: suspend (T) -> Unit,
) {
    private val channel = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch(dispatcher) {
            for (value in channel) {
                try {
                    write(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    fun submit(value: T) {
        channel.trySend(value)
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
