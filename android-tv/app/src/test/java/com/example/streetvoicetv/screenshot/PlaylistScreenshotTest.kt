package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.example.streetvoicetv.ui.common.LoadMoreState
import com.example.streetvoicetv.ui.playlist.PlaylistContent
import com.example.streetvoicetv.ui.playlist.PlaylistUiState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlaylistScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    @Test
    fun playlist_default() = compose.captureTv("playlist_default") {
        Playlist(PlaylistUiState(playlist = FakeData.playlists[0], songs = FakeData.songs))
    }

    @Test
    fun playlist_long_titles() = compose.captureTv("playlist_long_titles") {
        Playlist(PlaylistUiState(playlist = FakeData.longPlaylists[0], songs = FakeData.longSongs))
    }

    @Test
    fun playlist_no_header() = compose.captureTv("playlist_no_header") {
        Playlist(PlaylistUiState(playlist = null, songs = FakeData.songs))
    }

    @Test
    fun playlist_empty() = compose.captureTv("playlist_empty") {
        Playlist(PlaylistUiState(playlist = FakeData.playlists[2]))
    }

    @Test
    fun playlist_loading() = compose.captureTv("playlist_loading") { Playlist(PlaylistUiState(isLoading = true)) }

    @Test
    fun playlist_error() = compose.captureTv("playlist_error") {
        Playlist(PlaylistUiState(error = "Failed to load playlist: " + FakeData.LONG_EN + " " + FakeData.LONG_ZH))
    }

    @Test
    fun playlist_focused_first_song() = compose.captureTv(
        "playlist_focused_first_song",
        beforeCapture = { focusOnText(FakeData.longSongs[0].name) },
    ) {
        Playlist(PlaylistUiState(playlist = FakeData.longPlaylists[0], songs = FakeData.longSongs))
    }

    @Test
    fun playlist_with_now_playing() = compose.captureTv(
        "playlist_with_now_playing",
        nowPlaying = FakeData.playbackLong,
    ) {
        Playlist(PlaylistUiState(playlist = FakeData.playlists[0], songs = FakeData.songs))
    }

    /** The last song must stay visible above the NowPlayingBar when scrolled to the end. */
    @Test
    fun playlist_scrolled_to_end_with_now_playing() = compose.captureTv(
        "playlist_scrolled_to_end_with_now_playing",
        nowPlaying = FakeData.playbackLong,
        beforeCapture = {
            onNode(hasScrollToIndexAction()).performScrollToIndex(FakeData.longSongs.size)
        },
    ) {
        Playlist(PlaylistUiState(playlist = FakeData.longPlaylists[0], songs = FakeData.longSongs))
    }

    /** Paginated list scrolled to the end while the next page loads: small loading row. */
    @Test
    fun playlist_load_more_loading() = compose.captureTv(
        "playlist_load_more_loading",
        nowPlaying = FakeData.playbackLong,
        beforeCapture = {
            onNode(hasScrollToIndexAction()).performScrollToIndex(FakeData.songs.size + 1)
        },
    ) {
        Playlist(
            PlaylistUiState(
                playlist = FakeData.playlists[0],
                songs = FakeData.songs,
                loadMore = LoadMoreState(isLoadingMore = true, endReached = false),
            ),
        )
    }

    /** A page failed: focusable retry row at the end of the list (focused). */
    @Test
    fun playlist_load_more_error_focused() = compose.captureTv(
        "playlist_load_more_error_focused",
        nowPlaying = FakeData.playbackLong,
        beforeCapture = {
            onNode(hasScrollToIndexAction()).performScrollToIndex(FakeData.songs.size + 1)
            waitForIdle()
            focusOnText("載入更多失敗")
        },
    ) {
        Playlist(
            PlaylistUiState(
                playlist = FakeData.playlists[0],
                songs = FakeData.songs,
                loadMore = LoadMoreState(endReached = false, error = "網路連線失敗，請檢查網路後再試"),
            ),
        )
    }

    @Composable
    private fun Playlist(state: PlaylistUiState) {
        PlaylistContent(uiState = state, onSongSelected = { _, _ -> }, onRetry = {}, onBack = {})
    }
}
