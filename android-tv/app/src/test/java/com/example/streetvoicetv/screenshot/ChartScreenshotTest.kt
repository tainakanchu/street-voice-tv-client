package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.ui.chart.ChartContent
import com.example.streetvoicetv.ui.chart.ChartUiState
import com.example.streetvoicetv.ui.common.LoadMoreState
import com.example.streetvoicetv.ui.common.UserMessages
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class ChartScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    private val default = ChartUiState(songs = FakeData.songs, loadMore = LoadMoreState(endReached = false))

    @Test
    fun chart_default() = compose.captureTv("chart_default") { Chart(default) }

    @Test
    fun chart_genre_selected_weekly() = compose.captureTv("chart_genre_selected_weekly") {
        Chart(
            ChartUiState(
                period = ChartPeriod.Weekly,
                category = ChartCategory.bySlug("hip_hop"),
                categories = ChartCategory.forPeriod(ChartPeriod.Weekly),
                songs = FakeData.songs.reversed(),
            ),
        )
    }

    @Test
    fun chart_long_titles() = compose.captureTv("chart_long_titles") {
        Chart(ChartUiState(category = ChartCategory.bySlug("ai_generated"), songs = FakeData.longSongs))
    }

    @Test
    fun chart_loading() = compose.captureTv("chart_loading") { Chart(ChartUiState(isLoading = true)) }

    @Test
    fun chart_error() = compose.captureTv("chart_error") {
        Chart(ChartUiState(category = ChartCategory.bySlug("rock"), error = UserMessages.NETWORK))
    }

    @Test
    fun chart_empty() = compose.captureTv("chart_empty") { Chart(ChartUiState()) }

    @Test
    fun chart_with_now_playing() = compose.captureTv("chart_with_now_playing", nowPlaying = FakeData.playbackPlaying) {
        Chart(default)
    }

    @Composable
    private fun Chart(state: ChartUiState) {
        ChartContent(uiState = state, onSongSelected = { _, _ -> }, onSelectPeriod = {}, onSelectCategory = {})
    }
}
