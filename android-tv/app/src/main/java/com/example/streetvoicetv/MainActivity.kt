package com.example.streetvoicetv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.ui.album.AlbumScreen
import com.example.streetvoicetv.ui.artist.ArtistScreen
import com.example.streetvoicetv.ui.components.NowPlayingBar
import com.example.streetvoicetv.ui.player.PlayerScreen
import com.example.streetvoicetv.ui.search.SearchScreen
import com.example.streetvoicetv.ui.theme.StreetVoiceTvTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreetVoiceTvTheme {
                StreetVoiceTvApp(playbackManager)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreetVoiceTvApp(playbackManager: PlaybackManager) {
    val navController = rememberNavController()
    val playbackState by playbackManager.state.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showNowPlaying = playbackState.hasMedia && currentRoute?.startsWith("player") != true

    /** 曲リストの中から1曲を選んでキュー付きで再生開始 → プレイヤー画面へ */
    fun playSongInList(songs: List<Song>, selected: Song) {
        val index = songs.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        // キューをセット (HLS URL は PlayerViewModel or PlaybackManager が取得)
        playbackManager.setQueue(songs, index)
        navController.navigate("player/${selected.id}")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = "search",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("search") {
                SearchScreen(
                    onSongSelected = { song, allSongs ->
                        playSongInList(allSongs, song)
                    },
                    onArtistSelected = { artist ->
                        navController.navigate("artist/${artist.username}")
                    },
                )
            }

            composable(
                route = "player/{songId}",
                arguments = listOf(
                    navArgument("songId") { type = NavType.IntType },
                ),
            ) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "artist/{username}",
                arguments = listOf(
                    navArgument("username") { type = NavType.StringType },
                ),
            ) {
                ArtistScreen(
                    onSongSelected = { song, allSongs ->
                        playSongInList(allSongs, song)
                    },
                    onAlbumSelected = { album ->
                        navController.navigate("album/${album.id}")
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "album/{albumId}",
                arguments = listOf(
                    navArgument("albumId") { type = NavType.IntType },
                ),
            ) {
                AlbumScreen(
                    onSongSelected = { song, allSongs ->
                        playSongInList(allSongs, song)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (showNowPlaying) {
            NowPlayingBar(
                playbackState = playbackState,
                onTap = {
                    val songId = playbackState.song?.id
                    if (songId != null) {
                        navController.navigate("player/$songId")
                    }
                },
                onTogglePlayPause = { playbackManager.togglePlayPause() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            )
        }
    }
}
