package com.example.streetvoicetv.data.update

import android.os.StatFs
import java.io.File

/**
 * Free / total bytes of the partition apps are installed to (/data). Injected into
 * [UpdateChecker] so tests can fake it.
 */
interface StorageStats {
    /** `null` when the partition cannot be queried (the check is then skipped). */
    fun read(): Snapshot?

    data class Snapshot(val availableBytes: Long, val totalBytes: Long)
}

/** [StorageStats] of the partition holding [dir] (the app's filesDir lives on /data). */
class StatFsStorageStats(private val dir: File) : StorageStats {
    override fun read(): StorageStats.Snapshot? = try {
        val stat = StatFs(dir.path)
        StorageStats.Snapshot(availableBytes = stat.availableBytes, totalBytes = stat.totalBytes)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Frees the app's own reclaimable caches (image disk cache, HTTP cache) when there is not
 * enough room for an update. Blocking; called on a background dispatcher.
 */
fun interface CacheReclaimer {
    fun reclaim()
}

object UpdateStorage {
    const val MB = 1024L * 1024

    /** The system refuses installs below its low-storage threshold: min(5% of total, 500MB). */
    private const val LOW_STORAGE_PERCENT = 5
    private const val LOW_STORAGE_MAX_BYTES = 500 * MB

    /** Assumed APK size when the release asset does not report one. */
    const val DEFAULT_APK_SIZE = 10 * MB

    /** Before downloading: the APK copy, the installer's staging copy and dexopt output. */
    const val DOWNLOAD_APK_FACTOR = 3

    /** Before installing: the downloaded APK already takes its share. */
    const val INSTALL_APK_FACTOR = 2

    fun lowStorageThreshold(totalBytes: Long): Long =
        minOf(totalBytes * LOW_STORAGE_PERCENT / 100, LOW_STORAGE_MAX_BYTES)

    fun requiredBytes(totalBytes: Long, apkSize: Long, factor: Int): Long {
        val size = if (apkSize > 0) apkSize else DEFAULT_APK_SIZE
        return lowStorageThreshold(totalBytes) + size * factor
    }

    /** Bytes still missing (0 when there is enough room or the stats are unknown). */
    fun missingBytes(stats: StorageStats.Snapshot?, apkSize: Long, factor: Int): Long {
        if (stats == null || stats.totalBytes <= 0) return 0
        return (requiredBytes(stats.totalBytes, apkSize, factor) - stats.availableBytes).coerceAtLeast(0)
    }

    /** Whole megabytes, rounded up (for the dialog). */
    fun toMbCeil(bytes: Long): Long = (bytes + MB - 1) / MB
}
