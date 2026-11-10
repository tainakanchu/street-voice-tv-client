package com.example.streetvoicetv.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The player seen by the MediaSession (remote media keys, Assistant, system media controls).
 * The underlying ExoPlayer only ever holds one item, so next / previous are routed to
 * [PlaybackManager]'s queue and always advertised; play / pause / seek go through the manager
 * so that its error recovery (fresh stream URL) and position state stay in charge.
 */
@OptIn(UnstableApi::class)
internal class QueueForwardingPlayer(
    player: Player,
    private val manager: PlaybackManager,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*QUEUE_COMMANDS).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in QUEUE_COMMANDS || super.isCommandAvailable(command)

    override fun seekToNext() = manager.playNext()
    override fun seekToNextMediaItem() = manager.playNext()
    override fun seekToPrevious() = manager.playPrevious()
    override fun seekToPreviousMediaItem() = manager.playPrevious()
    override fun hasNextMediaItem(): Boolean = manager.state.value.hasNext
    override fun hasPreviousMediaItem(): Boolean = manager.state.value.hasPrevious

    override fun play() = manager.resume()
    override fun pause() = manager.pause()
    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) manager.resume() else manager.pause()
    }

    /** After an error, play() re-fetches the stream; a plain prepare() would replay the expired URL. */
    override fun prepare() {
        if (manager.state.value.error == null) super.prepare()
    }

    override fun seekTo(positionMs: Long) = manager.seekTo(positionMs)

    private companion object {
        val QUEUE_COMMANDS = intArrayOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
    }
}
