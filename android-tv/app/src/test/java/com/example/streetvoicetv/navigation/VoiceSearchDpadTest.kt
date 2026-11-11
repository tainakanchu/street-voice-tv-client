package com.example.streetvoicetv.navigation

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.streetvoicetv.Routes
import com.example.streetvoicetv.SEARCH_ROUTE
import com.example.streetvoicetv.data.SearchHistoryManager
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.ui.search.SearchContent
import com.example.streetvoicetv.ui.search.SearchUiState
import com.example.streetvoicetv.ui.search.SearchViewModel
import com.example.streetvoicetv.ui.search.VOICE_SEARCH_BUTTON_TAG
import com.example.streetvoicetv.ui.search.isVoiceSearchAvailable
import com.example.streetvoicetv.ui.search.voiceSearchIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The mic (voice search) button and the `search?q=` route. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class VoiceSearchDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private var voiceStarts = 0

    private fun setSearch(withVoice: Boolean) {
        compose.setContent {
            TvChrome {
                SearchContent(
                    uiState = SearchUiState(),
                    searchHistory = listOf("周杰倫", "米津玄師"),
                    onQueryChange = {},
                    onSearch = {},
                    onClearError = {},
                    onSelectHistory = {},
                    onSongSelected = { _, _ -> },
                    onArtistSelected = {},
                    onPlaylistSelected = {},
                    onVoiceSearch = if (withVoice) ({ voiceStarts++ }) else null,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun micButton_leftOfTheField_reachableAndStartsVoiceSearch() {
        setSearch(withVoice = true)
        compose.assertFocused(hasSetTextAction())
        compose.dpad(Dpad.Left)
        compose.assertFocusedTag(VOICE_SEARCH_BUTTON_TAG)
        compose.dpad(Dpad.Center)
        assertEquals(1, voiceStarts)
        compose.dpad(Dpad.Right)
        compose.assertFocused(hasSetTextAction())
        // Down from the mic reaches the history chips.
        compose.dpad(Dpad.Left, Dpad.Down)
        compose.assertFocusedText("周杰倫")
    }

    @Test
    fun noRecognizer_noMicButton() {
        setSearch(withVoice = false)
        assertTrue(compose.onAllNodes(hasTestTag(VOICE_SEARCH_BUTTON_TAG)).fetchSemanticsNodes().isEmpty())
        compose.dpad(Dpad.Left)
        compose.assertFocused(hasSetTextAction())
    }

    @Test
    fun recognizerAvailability_followsThePackageManager() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(isVoiceSearchAvailable(context))
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.example.recognizer"
                name = "Recognizer"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(voiceSearchIntent(null), info)
        assertTrue(isVoiceSearchAvailable(context))
        // The language request: zh-TW first.
        assertEquals("zh-TW", voiceSearchIntent("zh-TW").getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
    }

    @Test
    fun searchRouteWithQuery_fillsTheFieldAndSearches() {
        val repository = FakeStreetVoiceRepository().apply {
            searchSongsResult = Result.success(FakeData.songs.take(3))
            searchArtistsResult = Result.success(emptyList())
            searchPlaylistsResult = Result.success(emptyList())
        }
        val history = SearchHistoryManager(RuntimeEnvironment.getApplication())
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            TvChrome {
                NavHost(nav, startDestination = "home") {
                    composable("home") {}
                    composable(
                        route = SEARCH_ROUTE,
                        arguments = listOf(
                            navArgument(SearchViewModel.ARG_QUERY) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) {
                        val vm = viewModel { SearchViewModel(repository, history, createSavedStateHandle()) }
                        val state by vm.uiState.collectAsStateWithLifecycle()
                        SearchContent(
                            uiState = state,
                            searchHistory = emptyList(),
                            onQueryChange = vm::onQueryChange,
                            onSearch = vm::search,
                            onClearError = {},
                            onSelectHistory = {},
                            onSongSelected = { _, _ -> },
                            onArtistSelected = {},
                            onPlaylistSelected = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { nav.navigate(Routes.search("稻香 & 晴天")) }
        compose.waitForIdle()

        assertTrue("searchSongs(稻香 & 晴天)" in repository.calls)
        compose.onAllNodes(hasText("稻香 & 晴天")).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        // Focus moved to the first result.
        compose.assertFocusedText(FakeData.songs[0].name)
    }
}
