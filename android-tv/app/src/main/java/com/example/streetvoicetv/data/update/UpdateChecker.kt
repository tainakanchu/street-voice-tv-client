package com.example.streetvoicetv.data.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.streetvoicetv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub releases for a newer APK, downloads it and launches the system installer.
 *
 * - The check runs at most every [CHECK_INTERVAL_MS] (the last result is cached together with
 *   its ETag, and re-validated with If-None-Match), and never replaces a state other than Idle.
 * - The download is written to `updates/update.apk.part`, verified against the release asset
 *   size and only then renamed to `updates/update.apk` (the only directory the FileProvider
 *   exposes).
 * - Installs fail silently when /data is below the system's low-storage threshold, so free
 *   space is checked before downloading and before launching the installer (after first
 *   freeing the app's own caches via [CacheReclaimer]); a shortfall becomes
 *   [UpdateState.InsufficientStorage]. Leftover APKs from earlier updates are deleted when a
 *   check starts.
 */
@Singleton
class UpdateChecker internal constructor(
    private val updatesDir: File,
    private val prefs: SharedPreferences,
    baseClient: OkHttpClient,
    private val json: Json,
    private val currentVersion: String,
    private val releaseUrl: String,
    private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
    private val storageStats: StorageStats,
    private val cacheReclaimer: CacheReclaimer,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        json: Json,
        cacheReclaimer: CacheReclaimer,
    ) : this(
        updatesDir = File(context.cacheDir, UPDATES_DIR),
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        baseClient = client,
        json = json,
        currentVersion = BuildConfig.VERSION_NAME,
        releaseUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/latest",
        dispatcher = Dispatchers.IO,
        clock = System::currentTimeMillis,
        storageStats = StatFsStorageStats(context.filesDir),
        cacheReclaimer = cacheReclaimer,
    )

    // Shares the app client's connection pool / dispatcher. Its HTTP cache is disabled here:
    // the release check does its own ETag handling and APKs must not fill the API cache.
    // (The session interceptor only touches StreetVoice hosts.)
    private val client: OkHttpClient = baseClient.newBuilder()
        .cache(null)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /**
     * Guards [updatesDir] between the stale-APK cleanup and the start of a download: the
     * cleanup only deletes while the state is Idle, and a download sets Downloading before
     * creating its file under this lock, so a fresh download is never deleted.
     */
    private val filesLock = Any()

    /** Version the user answered "稍後" to in this process; not offered again until restart. */
    @Volatile private var dismissedVersion: String? = null

    /**
     * Checks for an update after [initialDelayMs] (so it does not compete with the first
     * screen's API calls). Does nothing while a check is running or the state is not Idle.
     */
    fun checkForUpdate(initialDelayMs: Long = 0) {
        if (_state.value != UpdateState.Idle) return
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            if (initialDelayMs > 0) delay(initialDelayMs)
            deleteStaleApks()
            try {
                val release = latestRelease() ?: return@launch
                val latestVersion = release.tagName.removePrefix("v")
                if (latestVersion == dismissedVersion) return@launch
                if (!isNewer(latestVersion, currentVersion)) return@launch
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@launch
                // Only Idle -> Available; never overwrite a download in progress etc.
                _state.compareAndSet(
                    UpdateState.Idle,
                    UpdateState.Available(
                        version = latestVersion,
                        downloadUrl = apkAsset.downloadUrl,
                        size = apkAsset.size,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Update check failed", e)
            }
        }
    }

    /**
     * 設定 → 檢查更新: checks right away, ignoring the [CHECK_INTERVAL_MS] throttle and an earlier
     * 稍後. A newer version becomes [UpdateState.Available] (the update dialog shows it).
     */
    suspend fun checkNow(): UpdateCheckResult {
        if (_state.value != UpdateState.Idle) return UpdateCheckResult.Busy
        checkJob?.cancel()
        return withContext(dispatcher) {
            try {
                val release = latestRelease(force = true) ?: return@withContext UpdateCheckResult.Failed
                val latestVersion = release.tagName.removePrefix("v")
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                if (!isNewer(latestVersion, currentVersion) || apkAsset == null) {
                    return@withContext UpdateCheckResult.UpToDate
                }
                dismissedVersion = null
                _state.compareAndSet(
                    UpdateState.Idle,
                    UpdateState.Available(version = latestVersion, downloadUrl = apkAsset.downloadUrl, size = apkAsset.size),
                )
                UpdateCheckResult.Available(latestVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Update check failed", e)
                UpdateCheckResult.Failed
            }
        }
    }

    fun downloadAndInstall() {
        val current = _state.value as? UpdateState.Available ?: return
        // Atomic: a second tap finds the state already Downloading and returns.
        if (!_state.compareAndSet(current, UpdateState.Downloading(0f))) return
        downloadJob = scope.launch {
            try {
                // Before downloading: room for the APK, the installer's copy and dexopt.
                val missing = ensureSpace(current.size, UpdateStorage.DOWNLOAD_APK_FACTOR, keep = null)
                if (missing > 0) {
                    _state.value = UpdateState.InsufficientStorage(missing, pending = current)
                    return@launch
                }
                val file = download(current)
                val ready = UpdateState.ReadyToInstall(file)
                // The APK itself took space: check again before the installer is launched.
                val missingForInstall = ensureSpace(file.length(), UpdateStorage.INSTALL_APK_FACTOR, keep = file)
                _state.value = if (missingForInstall > 0) {
                    UpdateState.InsufficientStorage(missingForInstall, pending = ready)
                } else {
                    ready
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Download failed", e)
                _state.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    /**
     * 儲存空間不足 → 重試: frees the caches again and re-checks. With enough room the pending step
     * continues (the download starts, or the installer is launched via `autoLaunch`);
     * otherwise the state stays [UpdateState.InsufficientStorage] with the new shortfall.
     */
    fun retryAfterStorage() {
        val current = _state.value as? UpdateState.InsufficientStorage ?: return
        if (current.isChecking) return
        when (val pending = current.pending) {
            is UpdateState.Available -> {
                // downloadAndInstall() frees caches and checks again.
                if (_state.compareAndSet(current, pending)) downloadAndInstall()
            }
            is UpdateState.ReadyToInstall -> {
                val checking = current.copy(isChecking = true)
                if (!_state.compareAndSet(current, checking)) return
                scope.launch {
                    val missing = try {
                        if (!pending.file.isFile) throw IOException("Update file missing")
                        ensureSpace(pending.file.length(), UpdateStorage.INSTALL_APK_FACTOR, keep = pending.file)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("Storage re-check failed", e)
                        _state.compareAndSet(checking, UpdateState.Error(e.message ?: "Install failed", isInstallError = true))
                        return@launch
                    }
                    _state.compareAndSet(
                        checking,
                        if (missing > 0) {
                            UpdateState.InsufficientStorage(missing, pending = pending)
                        } else {
                            pending.copy(autoLaunch = true, installerLaunched = false, needsPermission = false)
                        },
                    )
                }
            }
            else -> {}
        }
    }

    /**
     * Bytes still missing for an APK of [apkSize] (x [factor]). When short, first frees the
     * app's caches and leftover update files (except [keep]) and checks again. Blocking.
     */
    private fun ensureSpace(apkSize: Long, factor: Int, keep: File?): Long {
        val missing = UpdateStorage.missingBytes(storageStats.read(), apkSize, factor)
        if (missing <= 0) return 0
        log("Low storage: $missing bytes missing, freeing caches", null)
        try {
            cacheReclaimer.reclaim()
        } catch (e: Exception) {
            log("Freeing caches failed", e)
        }
        deleteUpdateFiles(keep)
        return UpdateStorage.missingBytes(storageStats.read(), apkSize, factor)
    }

    /** Deletes APKs of earlier updates (already installed, or abandoned) while nothing is in progress. */
    private fun deleteStaleApks() {
        synchronized(filesLock) {
            if (_state.value != UpdateState.Idle) return
            deleteUpdateFiles(keep = null)
        }
    }

    private fun deleteUpdateFiles(keep: File?) {
        updatesDir.listFiles()?.forEach { if (it != keep) it.delete() }
    }

    private suspend fun download(update: UpdateState.Available): File {
        val part = File(updatesDir, "$APK_NAME.part")
        val target = File(updatesDir, APK_NAME)
        synchronized(filesLock) {
            if (!updatesDir.isDirectory && !updatesDir.mkdirs()) throw IOException("Cannot create $updatesDir")
            part.createNewFile()
        }
        try {
            val request = Request.Builder().url(update.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: update.size
                var bytesRead = 0L
                var lastPercent = 0
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                // Emit in 1% steps instead of every buffer.
                                val percent = (bytesRead * 100 / totalBytes).toInt().coerceIn(0, 100)
                                if (percent > lastPercent) {
                                    lastPercent = percent
                                    _state.value = UpdateState.Downloading(percent / 100f)
                                }
                            }
                        }
                    }
                }
                val expected = if (update.size > 0) update.size else body.contentLength()
                if (expected > 0 && bytesRead != expected) {
                    throw IOException("Incomplete download: $bytesRead of $expected bytes")
                }
                if (bytesRead == 0L) throw IOException("Empty download")
            }
            if (target.exists() && !target.delete()) throw IOException("Cannot replace $target")
            if (!part.renameTo(target)) throw IOException("Cannot rename $part")
            return target
        } finally {
            part.delete()
        }
    }

    /**
     * Launches the system installer for a [UpdateState.ReadyToInstall] file. Without the
     * "install unknown apps" permission the matching settings screen is opened instead and
     * the dialog explains what to do. Never throws; failures become [UpdateState.Error].
     */
    fun installApk(activity: Activity) {
        val current = _state.value as? UpdateState.ReadyToInstall ?: return
        try {
            if (!current.file.isFile) throw IOException("Update file missing")
            // An installer launched without enough room fails without telling the user.
            val missing = UpdateStorage.missingBytes(
                storageStats.read(),
                current.file.length(),
                UpdateStorage.INSTALL_APK_FACTOR,
            )
            if (missing > 0) {
                _state.value = UpdateState.InsufficientStorage(missing, pending = current)
                return
            }
            if (!activity.packageManager.canRequestPackageInstalls()) {
                _state.value = current.copy(autoLaunch = false, needsPermission = true)
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"),
                    ),
                )
                return
            }
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                current.file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            // Stay on the dialog (with 安裝 / 關閉) so a cancelled installer is not a dead end.
            _state.value = current.copy(autoLaunch = false, needsPermission = false, installerLaunched = true)
        } catch (e: Exception) {
            // ActivityNotFoundException (no installer / settings screen), SecurityException, ...
            log("Install failed", e)
            _state.value = UpdateState.Error(e.message ?: "Install failed", isInstallError = true)
        }
    }

    fun dismiss() {
        when (val current = _state.value) {
            is UpdateState.Downloading -> return // cannot be dismissed (the dialog swallows Back)
            is UpdateState.Available -> dismissedVersion = current.version
            else -> {}
        }
        _state.value = UpdateState.Idle
    }

    /** The latest release, from cache within [CHECK_INTERVAL_MS], else re-validated via ETag. */
    private fun latestRelease(force: Boolean = false): GitHubRelease? {
        val now = clock()
        val cachedBody = prefs.getString(KEY_RELEASE_JSON, null)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (!force && cachedBody != null && now - lastCheck in 0 until CHECK_INTERVAL_MS) {
            return json.decodeFromString<GitHubRelease>(cachedBody)
        }

        val request = Request.Builder()
            .url(releaseUrl)
            .header("Accept", "application/vnd.github+json")
            .apply {
                val etag = prefs.getString(KEY_ETAG, null)
                if (etag != null && cachedBody != null) header("If-None-Match", etag)
            }
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 304 && cachedBody != null -> {
                    prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                    return json.decodeFromString<GitHubRelease>(cachedBody)
                }
                !response.isSuccessful -> {
                    log("Release check failed: HTTP ${response.code}", null)
                    return null
                }
                else -> {
                    val body = response.body?.string() ?: return null
                    val release = json.decodeFromString<GitHubRelease>(body)
                    prefs.edit()
                        .putLong(KEY_LAST_CHECK, now)
                        .putString(KEY_RELEASE_JSON, body)
                        .putString(KEY_ETAG, response.header("ETag"))
                        .apply()
                    return release
                }
            }
        }
    }

    private fun log(message: String, e: Throwable?) {
        try {
            Log.w(TAG, message, e)
        } catch (_: RuntimeException) {
            // android.util.Log is a stub in plain JVM tests.
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_REPO = "tainakanchu/street-voice-tv-client"
        private const val PREFS_NAME = "update_checker"
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_ETAG = "etag"
        private const val KEY_RELEASE_JSON = "release_json"

        /** Must match res/xml/file_paths.xml. */
        const val UPDATES_DIR = "updates"
        const val APK_NAME = "update.apk"

        /** Minimum time between two network checks. */
        const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

        /**
         * Semantic-version comparison: numeric core (missing parts are 0), then pre-release
         * ("1.2.0-rc1" < "1.2.0"; identifiers compared numerically or lexically per semver).
         * Build metadata ("+...") is ignored.
         */
        fun isNewer(latest: String, current: String): Boolean = compareVersions(latest, current) > 0

        fun compareVersions(a: String, b: String): Int {
            val (coreA, preA) = splitVersion(a)
            val (coreB, preB) = splitVersion(b)
            for (i in 0 until maxOf(coreA.size, coreB.size)) {
                val c = coreA.getOrElse(i) { 0L }.compareTo(coreB.getOrElse(i) { 0L })
                if (c != 0) return c
            }
            return when {
                preA == null && preB == null -> 0
                preA == null -> 1 // release > pre-release
                preB == null -> -1
                else -> comparePreRelease(preA, preB)
            }
        }

        private fun splitVersion(v: String): Pair<List<Long>, List<String>?> {
            val noBuild = v.trim().removePrefix("v").substringBefore('+')
            val core = noBuild.substringBefore('-')
            val pre = if ('-' in noBuild) noBuild.substringAfter('-').split('.') else null
            val parts = core.split('.').map { part -> part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L }
            return parts to pre
        }

        private fun comparePreRelease(a: List<String>, b: List<String>): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val x = a[i]
                val y = b[i]
                val xn = x.toLongOrNull()
                val yn = y.toLongOrNull()
                val c = when {
                    xn != null && yn != null -> xn.compareTo(yn)
                    xn != null -> -1 // numeric < alphanumeric
                    yn != null -> 1
                    else -> x.compareTo(y)
                }
                if (c != 0) return c
            }
            return a.size.compareTo(b.size)
        }
    }
}

/** Outcome of [UpdateChecker.checkNow]. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val version: String) : UpdateCheckResult
    data object Failed : UpdateCheckResult

    /** An update is already offered / downloading. */
    data object Busy : UpdateCheckResult
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data class Available(val version: String, val downloadUrl: String, val size: Long = 0) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()

    /**
     * The APK is downloaded. [autoLaunch]: the installer has not been started yet (the app
     * starts it once). [installerLaunched]: it was started (the user may have cancelled it).
     * [needsPermission]: the "install unknown apps" settings screen was opened instead.
     */
    data class ReadyToInstall(
        val file: File,
        val autoLaunch: Boolean = true,
        val installerLaunched: Boolean = false,
        val needsPermission: Boolean = false,
    ) : UpdateState()

    data class Error(val message: String, val isInstallError: Boolean = false) : UpdateState()

    /**
     * Not enough free space to download ([pending] is [Available]) or install ([pending] is
     * [ReadyToInstall]) the update, even after freeing the app's caches. [missingBytes] more
     * are needed. [isChecking]: 重試 is re-checking.
     */
    data class InsufficientStorage(
        val missingBytes: Long,
        val pending: UpdateState,
        val isChecking: Boolean = false,
    ) : UpdateState()
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)
