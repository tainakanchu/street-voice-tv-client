package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.requestFocus
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.components.NOW_PLAYING_BAR_TAG
import com.example.streetvoicetv.ui.search.SearchContent
import com.example.streetvoicetv.ui.search.SearchUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val SearchField = hasSetTextAction()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class SearchDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private var uiState by mutableStateOf(SearchUiState())

    private val history = listOf("周杰倫", "米津玄師", "落日飛車", "lofi", "Ed Sheeran")

    private val results = SearchUiState(
        query = "周",
        songs = FakeData.songs,
        artists = FakeData.artists,
        playlists = FakeData.playlists.take(3),
    )

    private fun setSearch(
        state: SearchUiState,
        history: List<String> = emptyList(),
        nowPlaying: PlaybackState = PlaybackState(),
    ) {
        uiState = state
        compose.setContent {
            TvChrome(nowPlaying = nowPlaying) {
                SearchContent(
                    uiState = uiState,
                    searchHistory = history,
                    onQueryChange = {},
                    onSearch = {},
                    onClearError = {},
                    onSelectHistory = {},
                    onSongSelected = { _, _ -> },
                    onArtistSelected = {},
                    onPlaylistSelected = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun initialFocus_onTextField() {
        setSearch(SearchUiState(), history)
        compose.assertFocused(SearchField)
    }

    @Test
    fun history_downFromTextFieldReachesChips_upReturns() {
        setSearch(SearchUiState(), history)
        compose.onNode(SearchField).requestFocus()
        compose.waitForIdle()

        compose.dpad(Dpad.Down)
        compose.assertFocused(hasText(history[0]))
        compose.dpad(Dpad.Right)
        compose.assertFocused(hasText(history[1]))
        compose.captureDpad("search_history_chip")

        compose.dpad(Dpad.Up)
        compose.assertFocused(SearchField)

        // Right from the (empty) text field reaches the 搜尋 button, Left returns.
        compose.dpad(Dpad.Right)
        compose.assertFocused(hasText("搜尋"))
        compose.dpad(Dpad.Left)
        compose.assertFocused(SearchField)
    }

    @Test
    fun resultsArrive_focusMovesToFirstResult_upReturnsToTextField() {
        setSearch(SearchUiState(query = "周"))
        compose.onNode(SearchField).requestFocus()
        compose.waitForIdle()
        compose.assertFocused(SearchField)

        uiState = results
        compose.waitForIdle()
        compose.assertFocusedText(FakeData.artists[0].displayName)

        compose.dpad(Dpad.Down, Dpad.Down)
        compose.assertFocusedText(FakeData.artists[2].displayName)

        compose.dpad(Dpad.Up, Dpad.Up)
        compose.assertFocusedText(FakeData.artists[0].displayName)
        compose.dpad(Dpad.Up)
        compose.assertFocused(SearchField)
        compose.captureDpad("search_up_to_text_field")

        // Down from the text field goes back into the results.
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(FakeData.artists[0].displayName)
    }

    @Test
    fun longResults_scrollKeepsFocusedItemVisible_endReachesNowPlayingBar() {
        val long = SearchUiState(
            query = FakeData.LONG_EN,
            songs = FakeData.longSongs,
            artists = FakeData.longArtists,
            playlists = FakeData.longPlaylists.take(5),
        )
        setSearch(long, nowPlaying = FakeData.playbackLong)
        compose.assertFocusedText(FakeData.longArtists[0].displayName)

        val total = long.artists.size + long.playlists.size + long.songs.size
        repeat(total - 1) { i ->
            compose.dpad(Dpad.Down)
            compose.assertFocusedFullyVisible()
            if (i == 12) compose.captureDpad("search_long_scrolled")
        }
        compose.assertFocusedText(long.songs.last().name)

        // Past the last result: the NowPlayingBar, and Up returns to the last result.
        compose.dpad(Dpad.Down)
        compose.assertFocusInside(NOW_PLAYING_BAR_TAG)
        compose.dpad(Dpad.Up)
        compose.assertFocusedText(long.songs.last().name)
        compose.assertFocusedFullyVisible()
    }
}
