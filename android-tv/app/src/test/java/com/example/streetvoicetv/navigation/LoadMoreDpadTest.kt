package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.test.hasText
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.album.AlbumContent
import com.example.streetvoicetv.ui.album.AlbumUiState
import com.example.streetvoicetv.ui.common.LoadMoreState
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.components.NOW_PLAYING_BAR_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * D-pad behaviour of paginated song lists (album screen as the representative): holding Down
 * through a long list keeps loading pages and the focus is never lost when items are appended.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class LoadMoreDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val album = FakeData.album(0)
    private val allSongs: List<Song> = List(TOTAL) { i ->
        FakeData.song(i).copy(id = 1000 + i, name = "第 ${i + 1} 首 ${FakeData.song(i).name}")
    }

    private var state by mutableStateOf(AlbumUiState())
    private var loadRequests by mutableIntStateOf(0)
    private var retries = 0
    private val played = mutableListOf<PlayAllMode>()

    private fun loadMoreState(size: Int, loading: Boolean = false, error: String? = null) =
        LoadMoreState(isLoadingMore = loading, endReached = size >= TOTAL, error = error, total = TOTAL)

    /**
     * Album screen backed by a fake paginated source: a load-more request takes a few frames
     * (like a network call) and then appends the next [PAGE] songs.
     */
    private fun setPagedAlbum(latencyFrames: Int = 4) {
        state = AlbumUiState(album = album, songs = allSongs.take(PAGE), loadMore = loadMoreState(PAGE))
        compose.setContent {
            TvChrome(nowPlaying = FakeData.playbackPlaying) {
                AlbumContent(
                    uiState = state,
                    onSongSelected = { _, _ -> },
                    onRetry = {},
                    onBack = {},
                    onPlayAll = { played += it },
                    onLoadMore = {
                        if (state.loadMore.canLoadMore) {
                            loadRequests++
                            state = state.copy(loadMore = loadMoreState(state.songs.size, loading = true))
                        }
                    },
                    onRetryLoadMore = { retries++ },
                )
            }
            // The "network": completes a pending page after a few frames.
            LaunchedEffect(loadRequests) {
                if (loadRequests == 0) return@LaunchedEffect
                repeat(latencyFrames) { withFrameNanos { } }
                val songs = allSongs.take(state.songs.size + PAGE)
                state = state.copy(songs = songs, loadMore = loadMoreState(songs.size))
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun holdingDown_loadsPagesAhead_focusFollowsEverySong() {
        setPagedAlbum()
        compose.assertFocusedText(allSongs[0].name)

        for (i in 1 until TOTAL) {
            compose.dpad(Dpad.Down)
            compose.assertFocusedText(allSongs[i].name)
            compose.assertFocusedFullyVisible()
            // Pages are requested ahead of the focus: the focused song is never the last loaded
            // one while more exist (so Down never has to wait at the end of the list).
            if (state.songs.size < TOTAL) {
                assertTrue("page loaded ahead of focus at $i", i < state.songs.size - 1)
            }
            if (i == 2 * PAGE + 3) compose.captureDpad("load_more_long_list")
        }
        assertEquals(TOTAL, state.songs.size)
        assertEquals(TOTAL / PAGE - 1, loadRequests)

        // End of the fully loaded list: Down reaches the NowPlayingBar as before.
        compose.dpad(Dpad.Down)
        compose.assertFocusInside(NOW_PLAYING_BAR_TAG)
    }

    @Test
    fun downOnLastLoadedSong_whileLoading_staysThenContinues() {
        setPagedAlbum()
        // A load is in flight and the user already is on the last loaded song.
        state = state.copy(loadMore = loadMoreState(PAGE, loading = true))
        compose.waitForIdle()
        compose.dpad(Dpad.Down, times = PAGE - 1)
        compose.assertFocusedText(allSongs[PAGE - 1].name)

        // Down must not leave the list (e.g. jump to the NowPlayingBar) while the page loads.
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(allSongs[PAGE - 1].name)

        state = state.copy(songs = allSongs.take(2 * PAGE), loadMore = loadMoreState(2 * PAGE))
        compose.waitForIdle()
        compose.assertFocusedText(allSongs[PAGE - 1].name)
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(allSongs[PAGE].name)
    }

    @Test
    fun failedPage_retryRowReachable_retryKeepsFocusInList() {
        setPagedAlbum()
        state = state.copy(loadMore = loadMoreState(PAGE, error = "網路連線失敗，請檢查網路後再試"))
        compose.waitForIdle()
        compose.dpad(Dpad.Down, times = PAGE - 1)
        compose.assertFocusedText(allSongs[PAGE - 1].name)

        compose.dpad(Dpad.Down)
        compose.assertFocusedText("載入更多失敗，重試")
        compose.captureDpad("load_more_retry_focused")

        // Retry: the row turns into a (non-focusable) loading row; focus goes back to the list.
        compose.dpad(Dpad.Center)
        state = state.copy(loadMore = loadMoreState(PAGE, loading = true))
        compose.waitForIdle()
        assertEquals(1, retries)
        compose.assertFocusedText(allSongs[PAGE - 1].name)
    }

    @Test
    fun playAllButtons_upFromFirstSong() {
        setPagedAlbum()
        compose.assertFocusedText(allSongs[0].name)
        compose.dpad(Dpad.Up)
        compose.assertFocused(hasText("播放全部"))
        compose.dpad(Dpad.Right)
        compose.assertFocused(hasText("隨機播放"))
        compose.dpad(Dpad.Center)
        assertEquals(listOf(PlayAllMode.Shuffle), played)
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(allSongs[0].name)
    }

    private companion object {
        const val PAGE = 20
        const val TOTAL = 80
    }
}
