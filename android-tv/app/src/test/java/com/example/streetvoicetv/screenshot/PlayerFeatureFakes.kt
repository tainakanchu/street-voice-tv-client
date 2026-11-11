package com.example.streetvoicetv.screenshot

import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.QueueItem
import com.example.streetvoicetv.playback.QueueView

/** Data for synced lyrics / ambient mode / autoplay queue (player feature tests). */
object PlayerFeatureFakes {

    private val lyricTexts = listOf(
        "走過了漫長的夜", "城市的燈一盞盞熄滅", "我還在等你的回覆", "", "風吹過空蕩的街",
        "把思念吹成一首歌", "唱給沒有你的明天", "我寧願所有痛苦都留在心裡", "也不願讓你看見",
        "天亮以後", "我們都會更好", "走過了漫長的夜", "還是會想起你",
    )

    /** Line i starts at (i * 5 + 3) s; line 3 is an instrumental gap. */
    val lrc: String = buildString {
        appendLine("[ti:漫長的夜]")
        appendLine("[ar:${FakeData.playerSong.artistName}]")
        lyricTexts.forEachIndexed { i, text ->
            val t = i * 5 + 3
            appendLine("[%02d:%02d.00]%s".format(t / 60, t % 60, text))
        }
    }

    fun lineTimeMs(index: Int): Long = (index * 5 + 3) * 1_000L

    val lrcSong: Song = FakeData.playerSong.copy(lyrics = lrc)

    /** Position inside line 7 ("我寧願所有痛苦都留在心裡"). */
    val playbackLrc: PlaybackState = FakeData.playbackPlaying.copy(song = lrcSong, positionMs = lineTimeMs(7) + 1_200)

    val playbackLongLrc: PlaybackState = FakeData.playbackLong.copy(
        song = FakeData.playerSongLong.copy(lyrics = lrc.replace("我寧願所有痛苦都留在心裡", FakeData.LONG_ZH)),
        isPlaying = true,
        positionMs = lineTimeMs(7) + 1_000,
    )

    /** 4 songs queued by the user (2nd playing), then 5 added by autoplay. */
    val autoplayQueue: QueueView = run {
        val user = List(4) { i -> if (i == 1) FakeData.playerSong else FakeData.song(i + 10) }
        val auto = List(5) { FakeData.song(i = it + 20) }
        QueueView(
            items = user.mapIndexed { i, s -> QueueItem(i, s) } + auto.mapIndexed { i, s -> QueueItem(4 + i, s, isAutoplay = true) },
            currentPosition = 1,
            autoplayEnabled = true,
        )
    }
}
