package com.example.streetvoicetv.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.example.streetvoicetv.domain.model.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 「用 StreetVoice 播放 …」 (Assistant / voice play-from-search): plays the songs found for the
 * query, the first one first with the rest queued. An empty query ("play some music") resumes
 * the last session, else plays [fallback] (the realtime chart). Returns false when nothing
 * could be found / played.
 */
internal class PlayFromSearch(
    private val search: suspend (query: String) -> List<Song>,
    private val fallback: suspend () -> List<Song>,
    private val playQueue: (songs: List<Song>, startIndex: Int) -> Unit,
    private val resumeLastSession: () -> Boolean,
) {
    suspend fun play(query: String): Boolean {
        val trimmed = query.trim()
        val songs = try {
            if (trimmed.isEmpty()) {
                if (resumeLastSession()) return true
                fallback()
            } else {
                search(trimmed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (songs.isEmpty()) return false
        playQueue(songs.distinctBy { it.id }, 0)
        return true
    }

    companion object {
        /**
         * Media id of the placeholder item handed back to the session after a search was played
         * through [PlaybackManager]; [QueueForwardingPlayer] ignores setMediaItems with it.
         */
        const val HANDLED_MEDIA_ID = "streetvoicetv:play-from-search"

        val handledItem: MediaItem = MediaItem.Builder().setMediaId(HANDLED_MEDIA_ID).build()

        fun isHandled(items: List<MediaItem>): Boolean =
            items.isNotEmpty() && items.all { it.mediaId == HANDLED_MEDIA_ID }
    }
}

/**
 * Session callback answering play-from-search requests (MediaSession `playFromSearch` /
 * `prepareFromSearch`, which Media3 turns into set-media-items requests carrying
 * [MediaItem.RequestMetadata.searchQuery]). Other requests keep the default behaviour.
 */
@OptIn(UnstableApi::class)
internal class PlayFromSearchCallback(
    private val playFromSearch: PlayFromSearch,
    private val scope: CoroutineScope,
) : MediaSession.Callback {

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
            ?: return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            if (playFromSearch.play(query)) {
                result.set(MediaSession.MediaItemsWithStartPosition(listOf(PlayFromSearch.handledItem), 0, C.TIME_UNSET))
            } else {
                result.setException(UnsupportedOperationException("Nothing found for the search"))
            }
        }
        return result
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> {
        // Adding search results to the queue is not supported (only playing them).
        if (mediaItems.any { it.requestMetadata.searchQuery != null }) {
            return Futures.immediateFailedFuture(UnsupportedOperationException())
        }
        return super.onAddMediaItems(mediaSession, controller, mediaItems)
    }
}
