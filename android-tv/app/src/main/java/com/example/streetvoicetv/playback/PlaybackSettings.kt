package com.example.streetvoicetv.playback

import android.content.Context

/** Persisted shuffle / repeat / autoplay preferences. */
interface PlaybackSettings {
    var shuffleEnabled: Boolean
    var repeatMode: RepeatMode

    /** 自動播放: keep playing similar songs when the queue ends (repeat off). Default on. */
    var autoplayEnabled: Boolean
}

internal class SharedPreferencesPlaybackSettings(context: Context) : PlaybackSettings {
    private val prefs by lazy { context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE) }

    override var shuffleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    override var repeatMode: RepeatMode
        get() = RepeatMode.entries.getOrElse(prefs.getInt(KEY_REPEAT, 0)) { RepeatMode.OFF }
        set(value) = prefs.edit().putInt(KEY_REPEAT, value.ordinal).apply()

    override var autoplayEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()

    private companion object {
        const val KEY_AUTOPLAY = "autoplay_enabled"
        const val KEY_SHUFFLE = "shuffle_enabled"
        const val KEY_REPEAT = "repeat_mode"
    }
}
