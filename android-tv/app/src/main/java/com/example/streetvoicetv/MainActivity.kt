package com.example.streetvoicetv

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.update.UpdateChecker
import com.example.streetvoicetv.data.update.UpdateState
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.ui.album.AlbumScreen
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.artist.ArtistScreen
import com.example.streetvoicetv.ui.components.BlockFocusEntry
import com.example.streetvoicetv.ui.components.NowPlayingBar
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.example.streetvoicetv.ui.components.tryRequestFocus
import com.example.streetvoicetv.ui.home.HomeScreen
import com.example.streetvoicetv.ui.login.LoginScreen
import com.example.streetvoicetv.ui.mypage.MyPageScreen
import com.example.streetvoicetv.ui.player.PlayerScreen
import com.example.streetvoicetv.ui.playlist.PlaylistScreen
import com.example.streetvoicetv.ui.search.SearchScreen
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset
import com.example.streetvoicetv.ui.theme.StreetVoiceTvTheme
import com.example.streetvoicetv.ui.update.UpdateDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playbackManager: PlaybackManager
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var updateChecker: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateChecker.checkForUpdate()
        setContent {
            StreetVoiceTvTheme {
                StreetVoiceTvApp(playbackManager, sessionManager, updateChecker, this)
            }
        }
    }
}

private const val PLAYER_ROUTE = "player/{songId}"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreetVoiceTvApp(
    playbackManager: PlaybackManager,
    sessionManager: SessionManager,
    updateChecker: UpdateChecker,
    activity: Activity,
) {
    val navController = rememberNavController()
    val playbackState by playbackManager.state.collectAsState()
    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()
    val profileImageUrl by sessionManager.profileImageUrl.collectAsState()
    val updateState by updateChecker.state.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // auto-launch installer when download completes
    if (updateState is UpdateState.ReadyToInstall) {
        updateChecker.installApk(activity)
    }

    val showNowPlaying = playbackState.hasMedia
        && currentRoute?.startsWith("player") != true
        && currentRoute != "login"

    // Replace any player already on the back stack instead of stacking duplicates.
    fun navigateToPlayer(songId: Int) {
        navController.navigate("player/$songId") {
            popUpTo(PLAYER_ROUTE) { inclusive = true }
            launchSingleTop = true
        }
    }

    // The queue is the list as shown: for paginated lists, only the songs loaded so far
    // (the rest is not fetched just to fill the queue).
    fun playSongInList(songs: List<Song>, selected: Song) {
        val index = songs.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        playbackManager.setQueue(songs, index)
        navigateToPlayer(selected.id)
    }

    // 播放全部 / 隨機播放. The screens hand over the songs loaded so far plus the remaining
    // pages up to PLAY_ALL_CAP. In order: shuffle off, start at the first song. Shuffle:
    // shuffle on, start at a random song (setQueue reshuffles the rest around it).
    fun playAll(songs: List<Song>, mode: PlayAllMode) {
        if (songs.isEmpty()) return
        val shuffle = mode == PlayAllMode.Shuffle
        if (playbackManager.state.value.shuffleEnabled != shuffle) playbackManager.toggleShuffle()
        val index = if (shuffle) songs.indices.random() else 0
        playbackManager.setQueue(songs, index)
        navigateToPlayer(songs[index].id)
    }

    AppScaffold(
        playbackState = playbackState,
        showNowPlaying = showNowPlaying,
        onNowPlayingTap = {
            val songId = playbackState.song?.id
            if (songId != null) navigateToPlayer(songId)
        },
        onTogglePlayPause = { playbackManager.togglePlayPause() },
        updateState = updateState,
        onUpdate = { updateChecker.downloadAndInstall() },
        onDismissUpdate = { updateChecker.dismiss() },
    ) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("home") {
                HomeScreen(
                    onSongSelected = { song, allSongs -> playSongInList(allSongs, song) },
                    onPlaylistSelected = { playlist ->
                        navController.navigate("playlist/${playlist.id}")
                    },
                    onSearchTap = { navController.navigate("search") },
                    onLoginTap = { navController.navigate("login") },
                    onMyPageTap = { navController.navigate("mypage") },
                    isLoggedIn = isLoggedIn,
                    profileImageUrl = profileImageUrl,
                )
            }

            composable("login") {
                LoginScreen(
                    sessionManager = sessionManager,
                    onLoginSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("search") {
                SearchScreen(
                    onSongSelected = { song, allSongs -> playSongInList(allSongs, song) },
                    onArtistSelected = { artist ->
                        navController.navigate("artist/${artist.username}")
                    },
                    onPlaylistSelected = { playlist ->
                        navController.navigate("playlist/${playlist.id}")
                    },
                )
            }

            composable(
                route = PLAYER_ROUTE,
                arguments = listOf(navArgument("songId") { type = NavType.IntType }),
            ) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onArtistSelected = { username ->
                        navController.navigate("artist/$username")
                    },
                )
            }

            composable(
                route = "artist/{username}",
                arguments = listOf(navArgument("username") { type = NavType.StringType }),
            ) {
                ArtistScreen(
                    onSongSelected = { song, allSongs -> playSongInList(allSongs, song) },
                    onPlayAll = ::playAll,
                    onAlbumSelected = { album -> navController.navigate("album/${album.id}") },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.IntType }),
            ) {
                AlbumScreen(
                    onSongSelected = { song, allSongs -> playSongInList(allSongs, song) },
                    onPlayAll = ::playAll,
                    onBack = { navController.popBackStack() },
                )
            }

            composable("mypage") {
                MyPageScreen(
                    onSongSelected = { song, allSongs -> playSongInList(allSongs, song) },
                    onPlayAll = ::playAll,
                    onPlaylistSelected = { playlist ->
                        navController.navigate("playlist/${playlist.id}")
                    },
                    onLoggedOut = { navController.popBackStack("home", inclusive = false) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.IntType }),
            ) {
                PlaylistScreen(
                    onSongSelected = { song, allSongs -> playSongInList(allSongs, song) },
                    onPlayAll = ::playAll,
                    onBack = { navController.popBackStack() },
                )
            }
        }

    }
}

/**
 * Stateless app chrome: background, screen content, the floating NowPlayingBar
 * and the update dialog overlay. Used by [StreetVoiceTvApp] and screenshot tests.
 *
 * Provides [LocalContentColor] = onBackground so text/icons without an explicit
 * colour stay readable on the dark background, and [LocalBottomOverlayInset] so
 * scrolling screens can keep their last items clear of the NowPlayingBar.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppScaffold(
    playbackState: PlaybackState,
    showNowPlaying: Boolean,
    onNowPlayingTap: () -> Unit,
    onTogglePlayPause: () -> Unit,
    updateState: UpdateState,
    onUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    content: @Composable () -> Unit,
) {
    val nowPlayingVisible = showNowPlaying && playbackState.song != null
    val contentFocus = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalBottomOverlayInset provides if (nowPlayingVisible) Dimens.NowPlayingBarInset else 0.dp,
        ) {
            val dialogVisible = updateState !is UpdateState.Idle
            // When the update dialog closes, focus would otherwise be left nowhere (its focused
            // button is gone): return it to the screen, where the restorers bring it back to
            // the element that was focused before the dialog appeared.
            var dialogShown by remember { mutableStateOf(false) }
            LaunchedEffect(dialogVisible) {
                if (dialogVisible) {
                    dialogShown = true
                } else if (dialogShown) {
                    dialogShown = false
                    contentFocus.requestFocusWhenReady()
                }
            }
            // While the update dialog is shown, D-pad focus must not reach the screen below.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (dialogVisible) BlockFocusEntry else Modifier),
            ) {
                // The screen is one focus group that remembers its focused child, so leaving the
                // NowPlayingBar returns to the element focus came from. (The scrolling screen
                // content lies under the floating bar, so plain geometric search from the bar
                // would skip it and jump to the top of the screen.)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(contentFocus)
                        .focusRestorer()
                        .focusGroup(),
                ) {
                    content()
                }

                if (nowPlayingVisible) {
                    NowPlayingBar(
                        playbackState = playbackState,
                        onTap = {
                            // The bar is hidden on the player screen. Removing it while it holds
                            // focus would drop focus altogether (the window re-focuses the nearest
                            // element of the outgoing screen and the player cannot take over), so
                            // hand focus back to the screen first, as if a card had been clicked.
                            contentFocus.tryRequestFocus()
                            onNowPlayingTap()
                        },
                        onTogglePlayPause = onTogglePlayPause,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = Dimens.SafeHorizontal, bottom = Dimens.SafeVertical)
                            .focusProperties { up = contentFocus },
                    )
                }
            }

            UpdateDialog(
                state = updateState,
                onUpdate = onUpdate,
                onDismiss = onDismissUpdate,
            )
        }
    }
}
