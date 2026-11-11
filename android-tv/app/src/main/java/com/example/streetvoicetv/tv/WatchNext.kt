package com.example.streetvoicetv.tv

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.RestorableSession

/**
 * What the Google TV home 「繼續觀看」(Watch Next) row shows for the current / last session:
 * one track with its art, the last position and a deep link back into the app.
 */
data class WatchNextSpec(
    val songId: Int,
    val title: String,
    val artist: String,
    val posterUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    /** Wall-clock time (epoch ms) of the last engagement (orders the Watch Next row). */
    val lastEngagementMs: Long,
)

/** What the Watch Next row should hold: our program ([Publish]) or nothing ([Remove]). */
sealed interface WatchNextTarget {
    data class Publish(val spec: WatchNextSpec) : WatchNextTarget
    data object Remove : WatchNextTarget
}

/** Pure mapping from playback state to the Watch Next program. */
object WatchNextMapping {

    /** Deep link of the program: resumes the last session and opens the player. */
    const val RESUME_URI = "streetvoicetv://resume"

    fun specOf(song: Song, positionMs: Long, durationMs: Long, nowMs: Long): WatchNextSpec {
        val duration = durationMs.takeIf { it > 0 }
            ?: (song.durationSeconds.takeIf { it.isFinite() && it > 0 }?.times(1000))?.toLong()
            ?: 0L
        val position = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        return WatchNextSpec(
            songId = song.id,
            title = song.name,
            artist = song.artistName,
            posterUrl = song.imageUrl ?: song.albumImageUrl,
            positionMs = position,
            durationMs = duration,
            lastEngagementMs = nowMs,
        )
    }

    /**
     * The program for the playing / loaded song, else for the saved session, else none.
     * [durationMs] is the player's duration of [song] (0 while unknown).
     */
    fun targetOf(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        restorable: RestorableSession?,
        nowMs: Long,
    ): WatchNextTarget = when {
        song != null -> WatchNextTarget.Publish(specOf(song, positionMs, durationMs, nowMs))
        restorable != null -> WatchNextTarget.Publish(
            specOf(restorable.currentSong, restorable.positionMs, 0, restorable.savedAtMs.takeIf { it > 0 } ?: nowMs),
        )
        else -> WatchNextTarget.Remove
    }

    /** The tvprovider program for [spec] (TYPE_TRACK, WATCH_NEXT_TYPE_CONTINUE). */
    @SuppressLint("RestrictedApi")
    fun buildProgram(spec: WatchNextSpec): WatchNextProgram {
        val builder = WatchNextProgram.Builder()
            .setType(TvContractCompat.WatchNextPrograms.TYPE_TRACK)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(spec.lastEngagementMs)
        builder
            .setTitle(spec.title)
            .setDescription(spec.artist)
        builder
            .setAuthor(spec.artist)
            .setLastPlaybackPositionMillis(spec.positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            .setDurationMillis(spec.durationMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            .setIntentUri(Uri.parse(RESUME_URI))
            .setInternalProviderId(spec.songId.toString())
        if (spec.posterUrl != null) {
            builder
                .setPosterArtUri(Uri.parse(spec.posterUrl))
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1)
        }
        return builder.build()
    }
}

/** Where the Watch Next program is written (the TV provider; a fake in tests). Blocking calls. */
interface WatchNextStore {
    fun publish(spec: WatchNextSpec)
    fun remove()
}

/**
 * [WatchNextStore] on the system TV provider. Keeps the id of our one program in preferences
 * so it is updated in place. Never throws: on devices without Leanback or without the provider
 * (or when the provider rejects us) it does nothing.
 */
internal class TvProviderWatchNextStore(private val context: Context) : WatchNextStore {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val supported: Boolean by lazy { isTvDevice(context) }

    override fun publish(spec: WatchNextSpec) {
        if (!supported) return
        try {
            val values = WatchNextMapping.buildProgram(spec).toContentValues()
            val resolver = context.contentResolver
            val id = prefs.getLong(KEY_PROGRAM_ID, -1L)
            if (id >= 0) {
                val updated = resolver.update(TvContractCompat.buildWatchNextProgramUri(id), values, null, null)
                if (updated > 0) return
            }
            val uri = resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values) ?: return
            prefs.edit().putLong(KEY_PROGRAM_ID, ContentUris.parseId(uri)).apply()
        } catch (e: Exception) {
            log("Could not publish the Watch Next program", e)
        }
    }

    override fun remove() {
        if (!supported) return
        val id = prefs.getLong(KEY_PROGRAM_ID, -1L)
        if (id < 0) return
        try {
            context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
        } catch (e: Exception) {
            log("Could not remove the Watch Next program", e)
        }
        prefs.edit().remove(KEY_PROGRAM_ID).apply()
    }

    private fun log(message: String, e: Throwable) {
        try {
            Log.w(TAG, message, e)
        } catch (_: RuntimeException) {
        }
    }

    private companion object {
        const val TAG = "WatchNext"
        const val PREFS_NAME = "watch_next"
        const val KEY_PROGRAM_ID = "program_id"
    }
}

/** Android TV / Google TV (Leanback). False when the check itself fails. */
internal fun isTvDevice(context: Context): Boolean = try {
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
} catch (_: Exception) {
    false
}
