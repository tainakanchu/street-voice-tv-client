package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.chart.CHART_GENRES_TAG
import com.example.streetvoicetv.ui.chart.CHART_TABS_TAG
import com.example.streetvoicetv.ui.chart.ChartContent
import com.example.streetvoicetv.ui.chart.ChartUiState
import com.example.streetvoicetv.ui.common.LoadMoreState
import org.junit.Assert.assertEquals
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
class ChartDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private var state by mutableStateOf(ChartUiState())
    private val loads = mutableListOf<String>()

    private fun songsFor(period: ChartPeriod, category: ChartCategory) =
        FakeData.songs.map { it.copy(id = it.id + 1000 * (period.ordinal + 1) + 100 * ChartCategory.ALL.indexOf(category)) }

    private fun select(period: ChartPeriod, category: ChartCategory) {
        // Like ChartViewModel: re-selecting the current chart does nothing.
        if (period == state.period && category == state.category && state.songs.isNotEmpty()) return
        loads += "${period.pathSegment}/${category.slug}"
        state = ChartUiState(
            period = period,
            category = category,
            categories = ChartCategory.forPeriod(period),
            songs = songsFor(period, category),
            loadMore = LoadMoreState(),
        )
    }

    private fun tab(label: String) = hasText(label) and hasAnyAncestor(hasTestTag(CHART_TABS_TAG))
    private fun chip(label: String) = hasText(label) and hasAnyAncestor(hasTestTag(CHART_GENRES_TAG))

    private fun setChart() {
        select(ChartPeriod.Realtime, ChartCategory.ALL.first())
        loads.clear()
        compose.setContent {
            val nav = rememberNavController()
            TvChrome {
                NavHost(nav, startDestination = "chart", modifier = Modifier.fillMaxSize()) {
                    composable("chart") {
                        ChartContent(
                            uiState = state,
                            onSongSelected = { _, _ -> nav.navigate("player") },
                            onSelectPeriod = { select(it, state.category.takeIf { c -> c.availableFor(it) } ?: ChartCategory.ALL.first()) },
                            onSelectCategory = { select(state.period, it) },
                        )
                    }
                    composable("player") {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocusWhenReady() }
                        Button(onClick = {}, modifier = Modifier.focusRequester(focus)) { Text("player") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun tabsChipsAndList_reachable() {
        setChart()
        val songs = state.songs
        compose.assertFocusedText(songs[0].name)

        // Up: play-all, then the selected chip, then the selected tab.
        compose.dpad(Dpad.Up)
        compose.assertFocused(hasText("播放全部"))
        compose.dpad(Dpad.Up)
        compose.assertFocused(chip("全部"))
        compose.dpad(Dpad.Up)
        compose.assertFocused(tab("即時"))

        // Right selects 週間 by focus.
        compose.dpad(Dpad.Right)
        compose.assertFocused(tab("週間"))
        assertEquals(listOf("weekly/all"), loads)

        // Down into the chips (the selected one), Right moves along, Center selects.
        compose.dpad(Dpad.Down)
        compose.assertFocused(chip("全部"))
        compose.dpad(Dpad.Right, Dpad.Right)
        compose.assertFocused(chip("民謠"))
        compose.dpad(Dpad.Center)
        assertEquals(listOf("weekly/all", "weekly/folk"), loads)
        compose.assertFocused(chip("民謠"))
        compose.captureDpad("chart_genre_selected")

        // Down reaches the new list.
        compose.dpad(Dpad.Down, Dpad.Down)
        compose.assertFocusedText(state.songs[0].name)
        compose.assertFocusedFullyVisible()

        // Back up into the chips restores the chip focused last.
        compose.dpad(Dpad.Up, Dpad.Up)
        compose.assertFocused(chip("民謠"))
    }

    @Test
    fun chipsScrollHorizontally_toTheLast() {
        setChart()
        compose.dpad(Dpad.Up, Dpad.Up)
        compose.assertFocused(chip("全部"))
        compose.dpad(Dpad.Right, times = ChartCategory.ALL.size)
        compose.assertFocused(chip("AI 生成"))
        compose.assertFocusedFullyVisible()
    }

    @Test
    fun backFromPlayer_restoresFocusedSong() {
        setChart()
        val songs = state.songs
        compose.dpad(Dpad.Down, Dpad.Down, Dpad.Down)
        compose.assertFocusedText(songs[3].name)
        compose.dpad(Dpad.Center)
        compose.onNodeWithTextExists("player")

        compose.dpad(Dpad.Back)
        compose.assertFocusedText(songs[3].name)
        compose.assertFocusedFullyVisible()
    }
}

private fun TvComposeRule.onNodeWithTextExists(text: String) {
    val nodes = onAllNodes(hasText(text)).fetchSemanticsNodes()
    assertEquals("Expected <$text> on screen", 1, nodes.size)
}
