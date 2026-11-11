package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.example.streetvoicetv.ui.album.AlbumContent
import com.example.streetvoicetv.ui.album.AlbumUiState
import com.example.streetvoicetv.ui.common.LoadMoreState
import com.example.streetvoicetv.ui.common.PlayAllMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class AlbumScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    @Test
    fun album_default() = compose.captureTv("album_default") {
        Album(AlbumUiState(album = FakeData.albums[0], songs = FakeData.songs))
    }

    @Test
    fun album_long_titles() = compose.captureTv("album_long_titles") {
        Album(
            AlbumUiState(
                album = FakeData.longAlbums[0].copy(name = FakeData.LONG_ZH + FakeData.LONG_EN),
                songs = FakeData.longSongs,
            ),
        )
    }

    @Test
    fun album_empty() = compose.captureTv("album_empty") {
        Album(AlbumUiState(album = FakeData.albums[1].copy(artistName = null)))
    }

    @Test
    fun album_loading() = compose.captureTv("album_loading") { Album(AlbumUiState(isLoading = true)) }

    @Test
    fun album_error() = compose.captureTv("album_error") {
        Album(AlbumUiState(error = "Failed to load album: " + FakeData.LONG_EN))
    }

    @Test
    fun album_focused_first_song() = compose.captureTv(
        "album_focused_first_song",
        beforeCapture = { focusOnText(FakeData.songs[0].name) },
    ) {
        Album(AlbumUiState(album = FakeData.albums[0], songs = FakeData.songs))
    }

    /** 隨機播放 pressed while the remaining songs load: spinner in place of its icon. */
    @Test
    fun album_play_all_pending() = compose.captureTv(
        "album_play_all_pending",
        beforeCapture = { focusOnText("隨機播放") },
    ) {
        Album(
            AlbumUiState(
                album = FakeData.albums[0],
                songs = FakeData.songs,
                loadMore = LoadMoreState(endReached = false),
                playAllPending = PlayAllMode.Shuffle,
            ),
        )
    }

    @Composable
    private fun Album(state: AlbumUiState) {
        AlbumContent(uiState = state, onSongSelected = { _, _ -> }, onRetry = {}, onBack = {})
    }
}
