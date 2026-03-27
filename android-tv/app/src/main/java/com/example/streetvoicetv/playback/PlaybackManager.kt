package com.example.streetvoicetv.playback

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.session.MediaSession
import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StreetVoiceRepository,
    private val api: StreetVoiceApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Queue
    private var queue: List<Song> = emptyList()
    private var currentIndex: Int = -1

    // Shuffle & Repeat
    private var shuffleEnabled = false
    private var repeatMode = RepeatMode.OFF
    private var shuffledIndices: List<Int> = emptyList()
    private var shufflePosition: Int = -1

    @OptIn(UnstableApi::class)
    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("StreetVoiceTV/1.0")
        .setDefaultRequestProperties(mapOf("Referer" to "https://streetvoice.com/"))

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            volume = 0.7f
            addListener(playerListener)
        }
    }

    val mediaSession: MediaSession by lazy {
        MediaSession.Builder(context, player).build()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) startProgressTracking() else stopProgressTracking()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _state.value = _state.value.copy(isPlaying = false)
                stopProgressTracking()
                if (repeatMode == RepeatMode.ONE) {
                    player.seekTo(0)
                    player.play()
                } else {
                    playNext()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                error = error.message ?: "Playback error",
                isPlaying = false,
            )
            stopProgressTracking()
        }
    }

    /** キューだけセットする (再生開始は PlayerViewModel が行う) */
    fun setQueue(songs: List<Song>, startIndex: Int) {
        queue = songs
        currentIndex = startIndex
        if (shuffleEnabled) generateShuffledIndices()
    }

    /** キュー付きで再生を開始する */
    fun playWithQueue(songs: List<Song>, startIndex: Int, hlsUrl: String) {
        queue = songs
        currentIndex = startIndex
        val song = songs[startIndex]
        startPlayback(song, hlsUrl)
    }

    /** 単曲再生 (キューなし) */
    fun play(song: Song, hlsUrl: String) {
        queue = listOf(song)
        currentIndex = 0
        startPlayback(song, hlsUrl)
    }

    /** キューが既にセットされていればそれを使い、なければ単曲再生 */
    fun playCurrentInQueue(song: Song, hlsUrl: String) {
        if (queue.isNotEmpty() && currentIndex in queue.indices && queue[currentIndex].id == song.id) {
            startPlayback(song, hlsUrl)
        } else {
            play(song, hlsUrl)
        }
    }

    @OptIn(UnstableApi::class)
    private fun startPlayback(song: Song, hlsUrl: String) {
        _state.value = PlaybackState(
            song = song,
            isPlaying = false,
            error = null,
            queueSize = queue.size,
            queueIndex = currentIndex,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
        )

        val mediaItem = MediaItem.Builder()
            .setUri(hlsUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumName)
                    .build()
            )
            .build()

        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        player.setMediaSource(hlsSource)
        player.prepare()
        player.playWhenReady = true

        // Report play event (fire-and-forget)
        scope.launch {
            try { api.reportPlay(song.id) } catch (_: Exception) { }
        }
    }

    /** 次の曲を再生 */
    fun playNext() {
        if (shuffleEnabled) {
            val nextPos = shufflePosition + 1
            if (nextPos >= shuffledIndices.size) {
                if (repeatMode == RepeatMode.ALL) {
                    generateShuffledIndices()
                    loadAndPlay(shuffledIndices[0])
                } else {
                    _state.value = _state.value.copy(isPlaying = false)
                }
                return
            }
            shufflePosition = nextPos
            loadAndPlay(shuffledIndices[shufflePosition])
        } else {
            val nextIndex = currentIndex + 1
            if (nextIndex >= queue.size) {
                if (repeatMode == RepeatMode.ALL) {
                    loadAndPlay(0)
                } else {
                    _state.value = _state.value.copy(isPlaying = false)
                }
                return
            }
            loadAndPlay(nextIndex)
        }
    }

    /** 前の曲を再生 */
    fun playPrevious() {
        // 再生位置が3秒以上なら曲頭に戻す
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }
        if (shuffleEnabled) {
            val prevPos = shufflePosition - 1
            if (prevPos < 0) {
                if (repeatMode == RepeatMode.ALL) {
                    shufflePosition = shuffledIndices.size - 1
                    loadAndPlay(shuffledIndices[shufflePosition])
                } else {
                    player.seekTo(0)
                }
                return
            }
            shufflePosition = prevPos
            loadAndPlay(shuffledIndices[shufflePosition])
        } else {
            val prevIndex = currentIndex - 1
            if (prevIndex < 0) {
                if (repeatMode == RepeatMode.ALL) {
                    loadAndPlay(queue.size - 1)
                } else {
                    player.seekTo(0)
                }
                return
            }
            loadAndPlay(prevIndex)
        }
    }

    private fun loadAndPlay(index: Int) {
        val song = queue[index]
        currentIndex = index
        _state.value = _state.value.copy(
            song = song,
            isPlaying = false,
            error = null,
            positionMs = 0,
            durationMs = 0,
            queueIndex = index,
        )

        scope.launch {
            repository.getStreamUrl(song.id)
                .onSuccess { stream ->
                    startPlayback(song, stream.hlsUrl)
                }
                .onFailure { error ->
                    Log.e("PlaybackManager", "Failed to load next track: ${error.message}")
                    _state.value = _state.value.copy(
                        error = "Failed to load next track: ${error.message}",
                    )
                }
        }
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled) {
            generateShuffledIndices()
        } else {
            shuffledIndices = emptyList()
            shufflePosition = -1
        }
        _state.value = _state.value.copy(shuffleEnabled = shuffleEnabled)
    }

    fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = repeatMode)
    }

    private fun generateShuffledIndices() {
        val indices = queue.indices.toMutableList()
        indices.remove(currentIndex)
        indices.shuffle()
        shuffledIndices = listOf(currentIndex) + indices
        shufflePosition = 0
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        currentIndex = -1
        _state.value = PlaybackState()
        stopProgressTracking()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun release() {
        stopProgressTracking()
        mediaSession.release()
        player.removeListener(playerListener)
        player.release()
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive) {
                val dur = player.duration.coerceAtLeast(0)
                val pos = player.currentPosition.coerceAtLeast(0)
                _state.value = _state.value.copy(
                    positionMs = pos,
                    durationMs = dur,
                )
                delay(500)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
