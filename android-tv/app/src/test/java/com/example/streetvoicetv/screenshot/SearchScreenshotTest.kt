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
import com.example.streetvoicetv.ui.search.SearchContent
import com.example.streetvoicetv.ui.search.SearchUiState
import com.example.streetvoicetv.ui.common.UserMessages

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class SearchScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    private val results = SearchUiState(
        query = "周杰倫",
        songs = FakeData.songs,
        artists = FakeData.artists,
        playlists = FakeData.playlists.take(3),
    )

    @Test
    fun search_initial() = compose.captureTv("search_initial") { Search(SearchUiState()) }

    @Test
    fun search_history() = compose.captureTv("search_history") {
        Search(SearchUiState(), history = listOf("周杰倫", "米津玄師", "落日飛車", "lofi", "Ed Sheeran"))
    }

    @Test
    fun search_history_long() = compose.captureTv("search_history_long") {
        Search(SearchUiState(), history = FakeData.longSearchHistory)
    }

    @Test
    fun search_loading() = compose.captureTv("search_loading") {
        Search(SearchUiState(query = "周杰倫", isLoading = true))
    }

    @Test
    fun search_results() = compose.captureTv("search_results") { Search(results) }

    @Test
    fun search_results_long() = compose.captureTv("search_results_long") {
        Search(
            SearchUiState(
                query = FakeData.LONG_EN,
                songs = FakeData.longSongs,
                artists = FakeData.longArtists,
                playlists = FakeData.longPlaylists.take(5),
            ),
        )
    }

    @Test
    fun search_results_focused() = compose.captureTv(
        "search_results_focused",
        beforeCapture = { focusOnText(FakeData.artists[0].displayName) },
    ) { Search(results) }

    @Test
    fun search_no_results_long_query() = compose.captureTv("search_no_results_long_query") {
        Search(SearchUiState(query = FakeData.LONG_ZH + FakeData.LONG_EN))
    }

    @Test
    fun search_error() = compose.captureTv("search_error") {
        Search(SearchUiState(query = "周杰倫", error = UserMessages.NETWORK))
    }

    @Test
    fun search_error_long() = compose.captureTv("search_error_long") {
        Search(
            SearchUiState(
                query = "周杰倫",
                error = "java.net.SocketTimeoutException: failed to connect to streetvoice.com/203.0.113.10 (port 443) from /192.168.1.23 (port 51234) after 10000ms: isConnected failed: ETIMEDOUT (Connection timed out) " + FakeData.LONG_ZH,
            ),
        )
    }

    @Test
    fun search_results_with_now_playing() = compose.captureTv(
        "search_results_with_now_playing",
        nowPlaying = FakeData.playbackLong,
    ) { Search(results) }

    @Test
    fun search_voice_button() = compose.captureTv("search_voice_button") {
        Search(SearchUiState(), history = listOf("周杰倫", "米津玄師", "落日飛車"), voice = true)
    }

    @Test
    fun search_voice_button_focused() = compose.captureTv(
        "search_voice_button_focused",
        beforeCapture = { focusOnContentDescription("語音搜尋") },
    ) {
        Search(results, voice = true)
    }

    @Composable
    private fun Search(state: SearchUiState, history: List<String> = emptyList(), voice: Boolean = false) {
        SearchContent(
            uiState = state,
            searchHistory = history,
            onQueryChange = {},
            onSearch = {},
            onClearError = {},
            onSelectHistory = {},
            onSongSelected = { _, _ -> },
            onArtistSelected = {},
            onPlaylistSelected = {},
            onVoiceSearch = if (voice) ({}) else null,
        )
    }
}
