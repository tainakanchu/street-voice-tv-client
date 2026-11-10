package com.example.streetvoicetv.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.example.streetvoicetv.domain.model.Song
import okhttp3.OkHttpClient

/**
 * The small slice of a media player that [PlaybackManager] drives. Lets the queue / load /
 * error logic be unit-tested with a fake, while production uses [ExoPlaybackEngine].
 * All calls happen on the main thread.
 */
interface PlaybackEngine {
    val isPlaying: Boolean
    val playWhenReady: Boolean
    /** One of [Player.STATE_IDLE], [Player.STATE_BUFFERING], [Player.STATE_READY], [Player.STATE_ENDED]. */
    val playbackState: Int
    val currentPosition: Long
    /** Duration in ms, or <= 0 when unknown. */
    val duration: Long

    fun setListener(listener: Listener)

    /** Replaces the media with [url] for [song], starting at [startPositionMs], and prepares it. */
    fun load(url: String, song: Song, startPositionMs: Long)
    fun play()
    fun pause()
    /** Stops and removes the current media (keeps play-when-ready). */
    fun clear()
    fun seekTo(positionMs: Long)
    fun release()

    /** The underlying Media3 player, for the MediaSession. */
    fun asPlayer(): Player

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onPlayWhenReadyChanged(playWhenReady: Boolean)
        fun onPlaybackStateChanged(state: Int)
        fun onError(error: PlaybackException)
    }
}

/** [PlaybackEngine] backed by ExoPlayer (HLS). The player is created on first use. */
@OptIn(UnstableApi::class)
class ExoPlaybackEngine(
    private val context: Context,
    private val httpClient: () -> OkHttpClient,
) : PlaybackEngine {

    private var listener: PlaybackEngine.Listener? = null

    private val dataSourceFactory by lazy {
        // Media requests must not carry the API session cookie / logging interceptors
        // (the previous DefaultHttpDataSource sent neither): share only connection pool & dispatcher.
        val mediaClient = httpClient().newBuilder().apply { interceptors().clear() }.build()
        OkHttpDataSource.Factory(mediaClient)
            .setUserAgent("StreetVoiceTV/1.0")
            .setDefaultRequestProperties(mapOf("Referer" to "https://streetvoice.com/"))
    }

    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                volume = 0.7f
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        listener?.onIsPlayingChanged(isPlaying)
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        listener?.onPlayWhenReadyChanged(playWhenReady)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        listener?.onPlaybackStateChanged(playbackState)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        listener?.onError(error)
                    }
                })
            }
    }

    override val isPlaying get() = player.isPlaying
    override val playWhenReady get() = player.playWhenReady
    override val playbackState get() = player.playbackState
    override val currentPosition get() = player.currentPosition.coerceAtLeast(0)
    override val duration get() = player.duration.let { if (it == C.TIME_UNSET) 0L else it.coerceAtLeast(0) }

    override fun setListener(listener: PlaybackEngine.Listener) {
        this.listener = listener
    }

    override fun load(url: String, song: Song, startPositionMs: Long) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumName)
                    .setArtworkUri(song.imageUrl?.let(Uri::parse))
                    .build()
            )
            .build()
        val source = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        player.setMediaSource(source, startPositionMs.coerceAtLeast(0))
        player.prepare()
    }

    override fun play() = player.play()
    override fun pause() = player.pause()

    override fun clear() {
        player.stop()
        player.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun release() = player.release()
    override fun asPlayer(): Player = player
}
