package com.example.streetvoicetv.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.components.CardRowVerticalPadding
import com.example.streetvoicetv.ui.components.InitialFocus
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.PlaylistCard
import com.example.streetvoicetv.ui.components.SongCard
import com.example.streetvoicetv.ui.components.focusRoot
import com.example.streetvoicetv.ui.components.initialFocus
import com.example.streetvoicetv.ui.components.rememberInitialFocus
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset

private val ScreenHPadding = Dimens.SafeHorizontal

/**
 * Test tags of the home sections (rows): continue, followed_new, recent, chart, genres, weekly,
 * editor, my_playlists, playlists.
 */
fun homeSectionTag(key: String) = "home_section_$key"

/** Test tag of the 「繼續播放」 card. */
const val CONTINUE_CARD_TAG = "home_continue_card"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onSearchTap: () -> Unit,
    onLoginTap: () -> Unit,
    onMyPageTap: () -> Unit,
    isLoggedIn: Boolean,
    profileImageUrl: String?,
    onPlayAll: (List<Song>, PlayAllMode) -> Unit = { _, _ -> },
    onChartTap: (genreSlug: String, period: ChartPeriod) -> Unit = { _, _ -> },
    /** Opens the player for the song that is now playing (after 繼續播放). */
    onOpenPlayer: (songId: Int) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Back on Home (from the player, an artist, ...): refresh stale 「追蹤音樂人的新歌」.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    HomeContent(
        uiState = uiState,
        isLoggedIn = isLoggedIn,
        profileImageUrl = profileImageUrl,
        onSongSelected = onSongSelected,
        onPlaylistSelected = onPlaylistSelected,
        onSearchTap = onSearchTap,
        onLoginTap = onLoginTap,
        onMyPageTap = onMyPageTap,
        onRetry = { viewModel.retry() },
        onPlayAll = onPlayAll,
        onChartTap = onChartTap,
        onRetryFollowed = { viewModel.retryFollowed() },
        onContinueListening = { viewModel.resumeLastSession()?.let(onOpenPlayer) },
    )
}

/** Stateless home content (testable without Hilt / network). */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    isLoggedIn: Boolean,
    profileImageUrl: String?,
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onSearchTap: () -> Unit,
    onLoginTap: () -> Unit,
    onMyPageTap: () -> Unit,
    onRetry: () -> Unit = {},
    onPlayAll: (List<Song>, PlayAllMode) -> Unit = { _, _ -> },
    onChartTap: (genreSlug: String, period: ChartPeriod) -> Unit = { _, _ -> },
    onRetryFollowed: () -> Unit = {},
    /** 繼續播放: resume the saved session. */
    onContinueListening: () -> Unit = {},
) {
    val bottomInset = LocalBottomOverlayInset.current
    val firstItemFocus = rememberInitialFocus()
    val followed = uiState.followedNew
    val continueListening = uiState.continueListening
    val hasContent = uiState.chartSongs.isNotEmpty() || uiState.editorPicks.isNotEmpty() ||
        uiState.myPlaylists.isNotEmpty() || uiState.playlists.isNotEmpty() ||
        uiState.weeklySongs.isNotEmpty() || followed?.songs?.isNotEmpty() == true ||
        continueListening != null || uiState.recentlyPlayed.isNotEmpty()
    // Whether the user has pressed any key on Home yet (see the 追蹤 row below).
    var userInteracted by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                userInteracted = true
                false
            }
            .focusRoot(firstItemFocus)
            .focusGroup(),
    ) {
        // Top bar: search + account
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenHPadding, end = ScreenHPadding, top = Dimens.SafeVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = onSearchTap,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "搜尋歌曲與音樂人...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (isLoggedIn) {
                Surface(
                    onClick = onMyPageTap,
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    modifier = Modifier.size(44.dp),
                ) {
                    AsyncImage(
                        model = profileImageUrl,
                        contentDescription = "我的頁面",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                Surface(
                    onClick = onLoginTap,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                ) {
                    Text(
                        text = "登入",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            !hasContent -> {
                MessageState(
                    title = if (uiState.error != null) "無法載入首頁" else null,
                    message = uiState.error ?: "目前沒有可顯示的內容",
                    isError = uiState.error != null,
                    primaryAction = MessageAction("重試", onRetry),
                    modifier = Modifier.padding(bottom = bottomInset),
                )
            }

            else -> {
                val followedSongs = followed?.songs.orEmpty()
                // The first section (top to bottom) gets the initial focus on its first card.
                val firstSection = when {
                    continueListening != null -> "continue"
                    followedSongs.isNotEmpty() -> "followed_new"
                    uiState.recentlyPlayed.isNotEmpty() -> "recent"
                    uiState.chartSongs.isNotEmpty() -> "chart"
                    uiState.weeklySongs.isNotEmpty() -> "weekly"
                    uiState.editorPicks.isNotEmpty() -> "editor"
                    uiState.myPlaylists.isNotEmpty() -> "my_playlists"
                    else -> "playlists"
                }
                // 「追蹤音樂人的新歌」 usually arrives after the other rows (it needs the follow
                // list first). When it does and the user has not touched the remote yet, move
                // the initial focus up to it, which also scrolls it into view at the top.
                // 「繼續播放」 (loaded from local storage, usually but not always before the rows
                // arrive) is the most likely action and takes precedence the same way.
                val followedFirstCard = remember { FocusRequester() }
                val continueCard = remember { FocusRequester() }
                val followedShown = followedSongs.isNotEmpty()
                val continueShown = continueListening != null
                LaunchedEffect(followedShown) {
                    if (followedShown && !continueShown && !userInteracted && firstItemFocus.isDone) {
                        followedFirstCard.requestFocusWhenReady()
                    }
                }
                LaunchedEffect(continueShown) {
                    if (continueShown && !userInteracted && firstItemFocus.isDone) {
                        continueCard.requestFocusWhenReady()
                    }
                }
                LazyColumn(
                    // Remembers the focused row, so returning (from the top bar or the
                    // NowPlayingBar) lands on the row - and, via the row's own restorer, the card -
                    // that was focused before.
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = Dimens.SafeVertical + bottomInset),
                ) {
                    if (continueListening != null) {
                        item(key = "continue") {
                            ContinueListeningSection(
                                continueListening = continueListening,
                                onClick = onContinueListening,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == "continue" },
                                cardFocus = continueCard,
                            )
                        }
                    }

                    if (followed != null && (followedSongs.isNotEmpty() || followed.error != null)) {
                        item(key = "followed_new") {
                            if (followedSongs.isNotEmpty()) {
                                SongCarouselSection(
                                    title = "追蹤音樂人的新歌",
                                    tag = homeSectionTag("followed_new"),
                                    songs = followed.queue,
                                    badges = followedSongs.map { it.ageLabel },
                                    onSongSelected = onSongSelected,
                                    firstItemFocus = firstItemFocus.takeIf { firstSection == "followed_new" },
                                    firstCardFocus = followedFirstCard,
                                    action = SectionAction("播放全部", Icons.Default.PlayArrow) {
                                        onPlayAll(followed.queue, PlayAllMode.InOrder)
                                    },
                                )
                            } else {
                                SectionError(
                                    title = "追蹤音樂人的新歌",
                                    tag = homeSectionTag("followed_new"),
                                    message = followed.error.orEmpty(),
                                    retrying = followed.isRetrying,
                                    onRetry = onRetryFollowed,
                                )
                            }
                        }
                    }

                    if (uiState.recentlyPlayed.isNotEmpty()) {
                        item(key = "recent") {
                            SongCarouselSection(
                                title = "最近播放",
                                tag = homeSectionTag("recent"),
                                songs = uiState.recentlyPlayedQueue,
                                badges = uiState.recentlyPlayed.map { it.ageLabel },
                                onSongSelected = onSongSelected,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == "recent" },
                            )
                        }
                    }

                    if (uiState.chartSongs.isNotEmpty()) {
                        item(key = "chart") {
                            SongCarouselSection(
                                title = "即時排行",
                                tag = homeSectionTag("chart"),
                                songs = uiState.chartSongs,
                                onSongSelected = onSongSelected,
                                showRank = true,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == "chart" },
                            )
                        }
                    }

                    if (uiState.genres.isNotEmpty()) {
                        item(key = "genres") {
                            GenreChipsSection(
                                genres = uiState.genres,
                                onGenreSelected = { onChartTap(it.slug, ChartPeriod.Realtime) },
                            )
                        }
                    }

                    if (uiState.weeklySongs.isNotEmpty()) {
                        item(key = "weekly") {
                            SongCarouselSection(
                                title = "本週排行",
                                tag = homeSectionTag("weekly"),
                                songs = uiState.weeklySongs,
                                onSongSelected = onSongSelected,
                                showRank = true,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == "weekly" },
                                action = SectionAction("查看全部", Icons.AutoMirrored.Filled.KeyboardArrowRight) {
                                    onChartTap(ChartCategory.ALL_SLUG, ChartPeriod.Weekly)
                                },
                            )
                        }
                    }

                    if (uiState.editorPicks.isNotEmpty()) {
                        item(key = "editor") {
                            SongCarouselSection(
                                title = "推薦單曲",
                                tag = homeSectionTag("editor"),
                                songs = uiState.editorPicks,
                                onSongSelected = onSongSelected,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == "editor" },
                            )
                        }
                    }

                    if (uiState.myPlaylists.isNotEmpty()) {
                        item(key = "my_playlists") {
                            PlaylistSection(
                                title = "我的歌單",
                                tag = homeSectionTag("my_playlists"),
                                playlists = uiState.myPlaylists,
                                onPlaylistSelected = onPlaylistSelected,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == "my_playlists" },
                            )
                        }
                    }

                    if (uiState.playlists.isNotEmpty()) {
                        item(key = "playlists") {
                            PlaylistSection(
                                title = "歌單",
                                tag = homeSectionTag("playlists"),
                                playlists = uiState.playlists,
                                onPlaylistSelected = onPlaylistSelected,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == "playlists" },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「繼續播放」: one wide card with the saved session's song, how far it got (progress bar and
 * time) and the queue size. OK resumes it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueListeningSection(
    continueListening: ContinueListening,
    onClick: () -> Unit,
    firstItemFocus: InitialFocus?,
    cardFocus: FocusRequester,
) {
    val song = continueListening.song
    Column(Modifier.testTag(homeSectionTag("continue"))) {
        SectionTitle("繼續播放")
        Box(Modifier.padding(horizontal = ScreenHPadding, vertical = CardRowVerticalPadding)) {
            Surface(
                onClick = onClick,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                modifier = Modifier
                    .width(ContinueCardWidth)
                    .testTag(CONTINUE_CARD_TAG)
                    .focusRequester(cardFocus)
                    .initialFocus(firstItemFocus),
            ) {
                Row(Modifier.height(ContinueCardHeight)) {
                    Box(contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = song.imageUrl,
                            contentDescription = song.name,
                            modifier = Modifier
                                .size(ContinueCardHeight)
                                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .padding(6.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProgressLine(continueListening.progress)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${Song.formatDuration(continueListening.positionMs / 1000.0)} / ${song.formattedDuration}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LocalContentColor.current.copy(alpha = 0.75f),
                                    maxLines = 1,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "佇列 ${continueListening.queueSize} 首",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LocalContentColor.current.copy(alpha = 0.75f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A thin progress bar (track in the content colour, progress in the primary colour). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProgressLine(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LocalContentColor.current.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private val ContinueCardWidth = 480.dp
private val ContinueCardHeight = 112.dp

/** A small button at the end of a section title (播放全部 / 查看全部). */
private class SectionAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = ScreenHPadding),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionTitleRow(title: String, action: SectionAction?) {
    if (action == null) {
        SectionTitle(title)
        return
    }
    Row(
        modifier = Modifier.padding(start = ScreenHPadding, end = ScreenHPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(16.dp))
        OutlinedButton(
            onClick = action.onClick,
            scale = ButtonDefaults.scale(focusedScale = 1.05f),
            contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(action.label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
            Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * A titled row of song cards. With an [action], the section is one focus group whose entry
 * (Up / Down from a neighbouring row) always lands on the cards; the title button is reached
 * with Up from the cards.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SongCarouselSection(
    title: String,
    songs: List<Song>,
    onSongSelected: (Song, List<Song>) -> Unit,
    tag: String,
    showRank: Boolean = false,
    firstItemFocus: InitialFocus? = null,
    action: SectionAction? = null,
    /** Image badges per song (e.g. 「3 天前」); used instead of the rank. */
    badges: List<String?>? = null,
    firstCardFocus: FocusRequester? = null,
) {
    val rowFocus = remember { FocusRequester() }
    Column(
        Modifier
            .testTag(tag)
            .then(
                if (action != null) {
                    Modifier
                        .focusProperties { onEnter = { rowFocus.requestFocus() } }
                        .focusGroup()
                } else {
                    Modifier
                },
            ),
    ) {
        SectionTitleRow(title, action)
        LazyRow(
            modifier = Modifier
                .focusRequester(rowFocus)
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = ScreenHPadding, vertical = CardRowVerticalPadding),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongCard(
                    song = song,
                    rank = if (showRank) index + 1 else null,
                    badge = badges?.getOrNull(index),
                    onClick = { onSongSelected(song, songs) },
                    modifier = Modifier
                        .then(if (index == 0 && firstCardFocus != null) Modifier.focusRequester(firstCardFocus) else Modifier)
                        .initialFocus(firstItemFocus, enabled = index == 0),
                )
            }
        }
    }
}

/** A section that failed to load: title, message and 重試. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionError(
    title: String,
    tag: String,
    message: String,
    retrying: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.testTag(tag)) {
        SectionTitle(title)
        Row(
            modifier = Modifier.padding(horizontal = ScreenHPadding, vertical = CardRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            OutlinedButton(onClick = onRetry, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                if (retrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("重試")
            }
        }
    }
}

/** 曲風: chart category chips; each opens the chart of that category. */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun GenreChipsSection(
    genres: List<ChartCategory>,
    onGenreSelected: (ChartCategory) -> Unit,
) {
    Column(Modifier.testTag(homeSectionTag("genres"))) {
        SectionTitle("曲風")
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = ScreenHPadding, vertical = 8.dp),
        ) {
            itemsIndexed(genres, key = { _, it -> it.slug }) { _, genre ->
                FilterChip(
                    selected = false,
                    onClick = { onGenreSelected(genre) },
                ) {
                    Text(genre.label)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlaylistSection(
    title: String,
    playlists: List<Playlist>,
    onPlaylistSelected: (Playlist) -> Unit,
    tag: String,
    firstItemFocus: InitialFocus? = null,
) {
    Column(Modifier.testTag(tag)) {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = ScreenHPadding, vertical = CardRowVerticalPadding),
        ) {
            itemsIndexed(playlists, key = { _, it -> it.id }) { index, playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistSelected(playlist) },
                    modifier = Modifier.initialFocus(firstItemFocus, enabled = index == 0),
                )
            }
        }
    }
}
