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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import com.example.streetvoicetv.ui.artist.ARTIST_FOLLOW_BUTTON_TAG
import com.example.streetvoicetv.ui.artist.ArtistContent
import com.example.streetvoicetv.ui.artist.FollowUiState
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.artist.ArtistTab
import com.example.streetvoicetv.ui.artist.ArtistUiState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class ArtistScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    private val default = ArtistUiState(
        artist = FakeData.artists[0],
        songs = FakeData.songs,
        albums = FakeData.albums,
    )

    @Test
    fun artist_default() = compose.captureTv("artist_default") { Artist(default) }

    @Test
    fun artist_albums_tab() = compose.captureTv("artist_albums_tab") {
        Artist(default.copy(selectedTab = ArtistTab.Albums))
    }

    @Test
    fun artist_long_titles() = compose.captureTv("artist_long_titles") {
        Artist(ArtistUiState(artist = FakeData.longArtists[0], songs = FakeData.longSongs, albums = FakeData.longAlbums))
    }

    @Test
    fun artist_long_albums_tab() = compose.captureTv("artist_long_albums_tab") {
        Artist(
            ArtistUiState(
                artist = FakeData.longArtists[0],
                songs = FakeData.longSongs,
                albums = FakeData.longAlbums,
                selectedTab = ArtistTab.Albums,
            ),
        )
    }

    @Test
    fun artist_empty() = compose.captureTv("artist_empty") {
        Artist(ArtistUiState(artist = FakeData.artists[1].copy(introduction = null)))
    }

    @Test
    fun artist_loading() = compose.captureTv("artist_loading") { Artist(ArtistUiState(isLoading = true)) }

    @Test
    fun artist_error() = compose.captureTv("artist_error") {
        Artist(ArtistUiState(error = "Failed to load artist: HTTP 404 Not Found"))
    }

    @Test
    fun artist_error_long() = compose.captureTv("artist_error_long") {
        Artist(ArtistUiState(error = "Failed to load artist: " + FakeData.LONG_EN + " " + FakeData.LONG_ZH + " " + FakeData.LONG_JA))
    }

    @Test
    fun artist_focused_first_song() = compose.captureTv(
        "artist_focused_first_song",
        beforeCapture = { focusOnText(FakeData.longSongs[0].name) },
    ) {
        Artist(ArtistUiState(artist = FakeData.longArtists[0], songs = FakeData.longSongs))
    }

    @Test
    fun artist_with_now_playing() = compose.captureTv(
        "artist_with_now_playing",
        nowPlaying = FakeData.playbackPlaying,
    ) { Artist(default) }

    @Test
    fun artist_follow_logged_in() = compose.captureTv("artist_follow_logged_in") {
        Artist(default.copy(follow = FollowUiState(isLoggedIn = true, isFollowing = false)))
    }

    @Test
    fun artist_following_focused() = compose.captureTv(
        "artist_following_focused",
        beforeCapture = { onNodeWithTag(ARTIST_FOLLOW_BUTTON_TAG).requestFocus() },
    ) {
        Artist(default.copy(follow = FollowUiState(isLoggedIn = true, isFollowing = true)))
    }

    @Test
    fun artist_follow_error() = compose.captureTv("artist_follow_error") {
        Artist(default.copy(follow = FollowUiState(isLoggedIn = true, error = UserMessages.FOLLOW_FAILED)))
    }

    @Test
    fun artist_following_tab() = compose.captureTv("artist_following_tab") {
        Artist(
            default.copy(
                following = FakeData.artists + FakeData.longArtists.mapIndexed { i, a -> a.copy(username = "long_$i") },
                selectedTab = ArtistTab.Following,
                follow = FollowUiState(isLoggedIn = true, isFollowing = true),
            ),
        )
    }

    @Composable
    private fun Artist(state: ArtistUiState) {
        ArtistContent(
            uiState = state,
            onSongSelected = { _, _ -> },
            onAlbumSelected = {},
            onRetry = {},
            onBack = {},
            onSelectTab = {},
        )
    }
}
