package com.example.streetvoicetv.screenshot

import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.RepeatMode

/** Deterministic fake data for screenshot tests (no network). */
object FakeData {

    private val songNames = listOf(
        "夜空中最亮的星", "小幸運", "Lemon", "光年之外", "說好不哭",
        "告白氣球", "マリーゴールド", "Shape of You", "稻香", "青花瓷",
        "後來", "演員",
    )
    private val artistNames = listOf(
        "逃跑計劃", "田馥甄", "米津玄師", "G.E.M. 鄧紫棋", "周杰倫",
        "五月天", "あいみょん", "Ed Sheeran", "落日飛車", "草東沒有派對",
    )

    fun song(i: Int, name: String = songNames[i % songNames.size], artist: String = artistNames[i % artistNames.size]) = Song(
        id = 1000 + i,
        name = name,
        artistName = artist,
        artistUsername = "artist$i",
        imageUrl = "https://example.invalid/song/$i.jpg",
        durationSeconds = 180.0 + i * 17,
        playsCount = 1234 * (i + 1) * (i + 1),
        likesCount = 12 * (i + 1),
        albumName = "專輯 ${i + 1}",
    )

    val songs: List<Song> = List(10) { song(it) }

    fun playlist(i: Int, name: String = "週末放鬆歌單 Vol.${i + 1}", curator: String? = "StreetVoice 編輯部") = Playlist(
        id = 2000 + i,
        name = name,
        imageUrl = "https://example.invalid/playlist/$i.jpg",
        description = "精選獨立音樂，陪你度過悠閒的週末午後。",
        songsCount = 20 + i,
        curatorName = curator,
    )

    val playlists: List<Playlist> = List(8) { playlist(it) }

    fun artist(i: Int, name: String = artistNames[i % artistNames.size], intro: String? = "來自台北的獨立樂團，用音樂記錄城市裡的日常。") = Artist(
        id = 3000 + i,
        username = "artist$i",
        displayName = name,
        profileImageUrl = "https://example.invalid/artist/$i.jpg",
        coverImageUrl = null,
        introduction = intro,
        followersCount = 15200 + i * 321,
        followingCount = 42 + i,
        songsCount = 37 + i,
    )

    val artists: List<Artist> = List(3) { artist(it) }

    fun album(i: Int, name: String = "第${i + 1}張專輯", artist: String? = artistNames[i % artistNames.size]) = Album(
        id = 4000 + i,
        name = name,
        imageUrl = "https://example.invalid/album/$i.jpg",
        songsCount = 10 + i,
        artistName = artist,
        artistUsername = "artist$i",
        createdAt = "2024-0${(i % 9) + 1}-01",
    )

    val albums: List<Album> = List(6) { album(it) }

    val playerSong = song(0).copy(
        synopsis = "這首歌寫給所有在夜裡仍然努力前進的人。",
        lyrics = List(12) { "第 ${it + 1} 行歌詞 — 我寧願所有痛苦都留在心裡" }.joinToString("\n"),
    )

    val playbackPlaying = PlaybackState(
        song = playerSong,
        isPlaying = true,
        positionMs = 83_000,
        durationMs = 245_000,
        queueSize = 10,
        queueIndex = 2,
    )

    // ---------------------------------------------------------------------
    // Stress data: very long multilingual strings, many items
    // ---------------------------------------------------------------------

    const val LONG_ZH = "在這個充滿喧囂與孤獨的城市裡我們依然相信總有一天會在某個轉角遇見最美好的自己與那些未曾說出口的溫柔"
    const val LONG_JA = "君の名前を呼ぶたびに世界がほんの少しだけ優しくなる気がしたあの夏の日の帰り道を僕はまだ覚えている"
    const val LONG_EN = "An Extraordinarily Long English Song Title That Keeps Going (Live at the Legendary Taipei Arena Acoustic Remix) [feat. Everyone]"
    const val LONG_ARTIST = "超級無敵長的樂團名稱 The Incredibly Long Named Orchestra & 東京ロングネーム合唱団"

    private val longTitles = listOf(LONG_ZH, LONG_JA, LONG_EN)

    val longSongs: List<Song> = List(30) {
        song(it, name = longTitles[it % 3] + " #${it + 1}", artist = LONG_ARTIST).copy(
            durationSeconds = 3600.0 + it, // 60:00+ durations
            playsCount = 98_765_432,
            likesCount = 1_234_567,
            albumName = "$LONG_ZH（豪華限定盤）",
        )
    }

    val longPlaylists: List<Playlist> = List(20) {
        playlist(it, name = longTitles[it % 3], curator = LONG_ARTIST).copy(
            description = "$LONG_ZH $LONG_JA $LONG_EN",
            songsCount = 9999,
        )
    }

    val longArtists: List<Artist> = List(5) {
        artist(it, name = LONG_ARTIST + " $it", intro = "$LONG_ZH $LONG_JA $LONG_EN").copy(
            followersCount = 123_456_789,
            songsCount = 99_999,
        )
    }

    val longAlbums: List<Album> = List(15) {
        album(it, name = longTitles[it % 3], artist = LONG_ARTIST).copy(songsCount = 999)
    }

    val longSearchHistory: List<String> = listOf(
        "周杰倫", "米津玄師", LONG_EN, "落日飛車", LONG_ZH, "lofi", LONG_JA,
        "草東沒有派對", "あいみょん", "indie rock taiwan", "Ed Sheeran", "告五人",
        "deca joins", "持修", "Sunset Rollercoaster", "魏如萱", "椅子樂團", "傷心欲絕", "YOASOBI", "宇多田ヒカル",
    )

    val playerSongLong = longSongs[0].copy(
        name = LONG_ZH + " " + LONG_EN,
        synopsis = List(4) { "$LONG_ZH $LONG_JA" }.joinToString("\n\n"),
        lyrics = List(60) { "${longTitles[it % 3]} (${it + 1})" }.joinToString("\n"),
    )

    val playbackLong = PlaybackState(
        song = playerSongLong,
        isPlaying = false,
        positionMs = 3_599_000,
        durationMs = 5_999_000,
        queueSize = 999,
        queueIndex = 998,
        shuffleEnabled = true,
        repeatMode = RepeatMode.ONE,
    )
}
