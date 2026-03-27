package com.example.streetvoicetv.data.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.example.streetvoicetv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkForUpdate() {
        scope.launch {
            try {
                val release = fetchLatestRelease() ?: return@launch
                val latestVersion = release.tagName.removePrefix("v")
                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewer(latestVersion, currentVersion)) {
                    val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    if (apkAsset != null) {
                        _state.value = UpdateState.Available(
                            version = latestVersion,
                            downloadUrl = apkAsset.downloadUrl,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Update check failed", e)
            }
        }
    }

    fun downloadAndInstall() {
        val current = _state.value
        if (current !is UpdateState.Available) return

        scope.launch {
            try {
                _state.value = UpdateState.Downloading(0f)

                val request = Request.Builder().url(current.downloadUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")

                val body = response.body ?: throw IOException("Empty response")
                val totalBytes = body.contentLength()
                val file = File(context.cacheDir, "update.apk")

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                _state.value = UpdateState.Downloading(
                                    bytesRead.toFloat() / totalBytes.toFloat()
                                )
                            }
                        }
                    }
                }

                _state.value = UpdateState.ReadyToInstall(file)
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Download failed", e)
                _state.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun installApk(activity: Activity) {
        val current = _state.value
        if (current !is UpdateState.ReadyToInstall) return

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
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private fun fetchLatestRelease(): GitHubRelease? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        return json.decodeFromString<GitHubRelease>(body)
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    companion object {
        private const val GITHUB_REPO = "tainakanchu/street-voice-tv-client"
    }
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data class Available(val version: String, val downloadUrl: String) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
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
