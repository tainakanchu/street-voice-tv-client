package com.example.streetvoicetv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.example.streetvoicetv.ui.player.PlayerScreen
import com.example.streetvoicetv.ui.search.SearchScreen
import com.example.streetvoicetv.ui.theme.StreetVoiceTvTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreetVoiceTvTheme {
                StreetVoiceTvApp()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreetVoiceTvApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "search",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        composable("search") {
            SearchScreen(
                onSongSelected = { song ->
                    navController.navigate("player/${song.id}")
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
    }
}
