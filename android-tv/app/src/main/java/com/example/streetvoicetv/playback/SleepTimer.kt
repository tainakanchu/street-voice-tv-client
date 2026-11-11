package com.example.streetvoicetv.playback

import androidx.compose.runtime.Immutable

/** Choices of the sleep timer (睡眠計時) menu, in menu order. */
enum class SleepTimerOption(val label: String, val minutes: Int?) {
    MIN_15("15 分", 15),
    MIN_30("30 分", 30),
    MIN_60("60 分", 60),
    MIN_90("90 分", 90),
    END_OF_SONG("本曲結束", null),
    OFF("關閉", null),
}

/** State of [PlaybackManager.sleepTimer]. */
@Immutable
sealed interface SleepTimerState {
    /** No timer. */
    data object Off : SleepTimerState

    /** Pauses when [remainingMs] reaches 0 (the volume fades out over the last 10 s). Ticks once a second. */
    data class Countdown(val remainingMs: Long) : SleepTimerState

    /** Pauses when the current song ends (fading out over its last 10 s). */
    data object EndOfSong : SleepTimerState

    val isActive: Boolean get() = this != Off
}

/** "29:41" (or "1:29:41") for the remaining time of a countdown, rounded up to the second. */
fun formatRemaining(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
