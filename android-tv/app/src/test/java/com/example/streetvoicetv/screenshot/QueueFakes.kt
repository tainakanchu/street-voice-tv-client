package com.example.streetvoicetv.screenshot

import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.QueueItem
import com.example.streetvoicetv.playback.QueueView
import com.example.streetvoicetv.playback.RepeatMode
import kotlin.random.Random

/** Play queues matching [FakeData]'s playback states (for the player's 佇列 panel). */
object QueueFakes {

    /** 10 songs; the 3rd (queue index 2) is [FakeData.playerSong], as in [FakeData.playbackPlaying]. */
    val songs: List<Song> = List(10) { i -> if (i == 2) FakeData.playerSong else FakeData.song(i + 1) }

    private fun view(
        songs: List<Song>,
        order: List<Int>,
        currentQueueIndex: Int,
        shuffle: Boolean = false,
        repeat: RepeatMode = RepeatMode.OFF,
    ) = QueueView(
        items = order.map { QueueItem(it, songs[it]) },
        currentPosition = order.indexOf(currentQueueIndex),
        shuffleEnabled = shuffle,
        repeatMode = repeat,
    )

    /** In order, 3rd song playing (2 played, 7 up next). */
    val normal: QueueView = view(songs, songs.indices.toList(), currentQueueIndex = 2)

    /** Shuffled order with the current song 3rd in it. */
    val shuffled: QueueView = view(songs, listOf(7, 4, 2, 9, 0, 5, 1, 8, 3, 6), currentQueueIndex = 2, shuffle = true)
    val playbackShuffled: PlaybackState = FakeData.playbackPlaying.copy(shuffleEnabled = true)

    /** The last song is playing: nothing follows. */
    val atEnd: QueueView = view(songs, songs.indices.toList(), currentQueueIndex = 9)
    val playbackAtEnd: PlaybackState = FakeData.playbackPlaying.copy(
        song = songs[9],
        queueIndex = 9,
        nextAvailable = false,
    )

    /** 999 long-titled songs (as [FakeData.playbackLong]: shuffle + repeat ONE), current one 41st in the order. */
    val long: QueueView = run {
        val longSongs = List(999) { i -> if (i == 998) FakeData.playerSongLong else FakeData.longSongs[i % FakeData.longSongs.size] }
        val order = (0 until 998).shuffled(Random(7)).toMutableList().apply { add(40, 998) }
        view(longSongs, order, currentQueueIndex = 998, shuffle = true, repeat = RepeatMode.ONE)
    }
}
