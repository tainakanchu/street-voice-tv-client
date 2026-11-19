package com.example.streetvoicetv.data.update

import android.app.Activity
import android.app.Application
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateCheckerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("update_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    private var now = 1_000_000_000L

    // Fake /data: 4GB total -> low-storage threshold 200MB.
    private val totalBytes = 4L * 1024 * 1024 * 1024
    private var availableBytes = totalBytes / 2
    private var reclaimCount = 0
    private var onReclaim: () -> Unit = {}
    private var storageReader: () -> Long = { availableBytes }
    private val storageStats = object : StorageStats {
        override fun read() = StorageStats.Snapshot(storageReader(), totalBytes)
    }
    private val threshold = UpdateStorage.lowStorageThreshold(totalBytes)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun checker(
        current: String = "1.0.0",
        dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
    ) = UpdateChecker(
        updatesDir = tmp.root.resolve("updates"),
        prefs = prefs,
        baseClient = OkHttpClient(),
        json = Json { ignoreUnknownKeys = true },
        currentVersion = current,
        releaseUrl = server.url("/releases/latest").toString(),
        dispatcher = dispatcher,
        clock = { now },
        storageStats = storageStats,
        cacheReclaimer = CacheReclaimer {
            reclaimCount++
            onReclaim()
        },
    )

    private fun releaseJson(tag: String, size: Long) = """
        {"tag_name":"$tag","assets":[
          {"name":"notes.txt","browser_download_url":"${server.url("/notes")}","size":1},
          {"name":"app-release.apk","browser_download_url":"${server.url("/app.apk")}","size":$size}
        ]}
    """.trimIndent()

    private fun enqueueRelease(tag: String = "v1.1.0", size: Long = 4, etag: String = "\"e1\"") =
        server.enqueue(MockResponse().setBody(releaseJson(tag, size)).setHeader("ETag", etag))

    // ------------------------------------------------------------------ version compare

    @Test
    fun `isNewer follows semver including pre-releases`() {
        val newer = listOf(
            "1.0.1" to "1.0.0",
            "1.10.0" to "1.9.9",
            "2.0" to "1.99.99",
            "1.2.0" to "1.2.0-rc1",
            "1.2.0-rc2" to "1.2.0-rc1",
            "1.2.0-rc.10" to "1.2.0-rc.2",
            "1.2.0-beta" to "1.2.0-alpha",
            "1.2.0-alpha.1" to "1.2.0-alpha",
            "1.2.0-alpha.beta" to "1.2.0-alpha.1",
            "v1.2.1" to "1.2.0",
        )
        for ((a, b) in newer) {
            assertTrue("$a > $b", UpdateChecker.isNewer(a, b))
            assertFalse("$b < $a", UpdateChecker.isNewer(b, a))
        }
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.2.0+build.5", "1.2.0+build.4"))
        assertFalse(UpdateChecker.isNewer("1.2.0-rc1", "1.2.0"))
    }

    // ------------------------------------------------------------------ check

    @Test
    fun `newer release becomes Available with the apk asset`() {
        enqueueRelease(size = 123)
        val checker = checker()
        checker.checkForUpdate()
        val state = checker.state.value as UpdateState.Available
        assertEquals("1.1.0", state.version)
        assertTrue(state.downloadUrl.endsWith("/app.apk"))
        assertEquals(123, state.size)
    }

    @Test
    fun `same or older release stays Idle`() {
        enqueueRelease(tag = "v1.0.0")
        val checker = checker()
        checker.checkForUpdate()
        assertEquals(UpdateState.Idle, checker.state.value)
    }

    @Test
    fun `checks are throttled and revalidated with the ETag`() {
        enqueueRelease()
        checker().checkForUpdate()
        assertEquals(1, server.requestCount)

        // Within the interval: answered from the cached release, no request.
        now += UpdateChecker.CHECK_INTERVAL_MS / 2
        val second = checker()
        second.checkForUpdate()
        assertEquals(1, server.requestCount)
        assertTrue(second.state.value is UpdateState.Available)

        // After the interval: conditional request; 304 reuses the cached release.
        now += UpdateChecker.CHECK_INTERVAL_MS
        server.enqueue(MockResponse().setResponseCode(304))
        val third = checker()
        third.checkForUpdate()
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertEquals("\"e1\"", server.takeRequest().getHeader("If-None-Match"))
        assertTrue(third.state.value is UpdateState.Available)
    }

    @Test
    fun `check never replaces a non-Idle state and dismissed version is not offered again`() {
        enqueueRelease()
        val checker = checker()
        checker.checkForUpdate()
        assertTrue(checker.state.value is UpdateState.Available)

        checker.checkForUpdate() // not Idle: no-op
        assertEquals(1, server.requestCount)

        checker.dismiss()
        assertEquals(UpdateState.Idle, checker.state.value)
        checker.checkForUpdate() // cached release, same version as dismissed
        assertEquals(UpdateState.Idle, checker.state.value)
    }

    @Test
    fun `failed check stays Idle and closes the response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("err"))
        val checker = checker()
        checker.checkForUpdate()
        assertEquals(UpdateState.Idle, checker.state.value)
    }

    // ------------------------------------------------------------------ download

    @Test
    fun `download writes updates apk and becomes ReadyToInstall`() {
        enqueueRelease(size = 4)
        val checker = checker()
        checker.checkForUpdate()
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3, 4))))
        checker.downloadAndInstall()

        val state = checker.state.value as UpdateState.ReadyToInstall
        assertEquals(tmp.root.resolve("updates/update.apk"), state.file)
        assertEquals(4, state.file.length())
        assertTrue(state.autoLaunch)
        assertFalse(tmp.root.resolve("updates/update.apk.part").exists())
    }

    @Test
    fun `size mismatch is an error and leaves no file behind`() {
        enqueueRelease(size = 10)
        val checker = checker()
        checker.checkForUpdate()
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3))))
        checker.downloadAndInstall()

        assertTrue(checker.state.value is UpdateState.Error)
        assertEquals(emptyList<String>(), tmp.root.resolve("updates").list()?.toList().orEmpty())
    }

    @Test
    fun `http error during download is an error`() {
        enqueueRelease()
        val checker = checker()
        checker.checkForUpdate()
        server.enqueue(MockResponse().setResponseCode(404))
        checker.downloadAndInstall()
        val state = checker.state.value as UpdateState.Error
        assertFalse(state.isInstallError)
    }

    @Test
    fun `double tap starts a single download`() {
        val dispatcher = StandardTestDispatcher()
        enqueueRelease(size = 4)
        val checker = checker(dispatcher = dispatcher)
        checker.checkForUpdate()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(checker.state.value is UpdateState.Available)

        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3, 4))))
        checker.downloadAndInstall()
        checker.downloadAndInstall()
        assertTrue(checker.state.value is UpdateState.Downloading)
        checker.dismiss() // cannot dismiss while downloading
        assertTrue(checker.state.value is UpdateState.Downloading)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(checker.state.value is UpdateState.ReadyToInstall)
        assertEquals(2, server.requestCount) // release + one apk download
        server.takeRequest()
        assertEquals("/app.apk", server.takeRequest().path)
        assertNull(server.takeRequest(10, TimeUnit.MILLISECONDS))
    }

    // ------------------------------------------------------------------ storage

    @Test
    fun `required space is the low-storage threshold plus three apk sizes`() {
        val mb = UpdateStorage.MB
        assertEquals(200 * mb, UpdateStorage.lowStorageThreshold(4000 * mb))
        assertEquals(500 * mb, UpdateStorage.lowStorageThreshold(64_000 * mb))
        assertEquals(200 * mb + 3 * 20 * mb, UpdateStorage.requiredBytes(4000 * mb, 20 * mb, 3))
        // Unknown size: 10MB assumed.
        assertEquals(200 * mb + 30 * mb, UpdateStorage.requiredBytes(4000 * mb, 0, 3))
        assertEquals(0, UpdateStorage.missingBytes(null, 20 * mb, 3))
        assertEquals(2, UpdateStorage.toMbCeil(mb + 1))
    }

    @Test
    fun `insufficient space before download frees caches and becomes InsufficientStorage`() {
        val apkSize = 20 * UpdateStorage.MB
        enqueueRelease(size = apkSize)
        val checker = checker()
        checker.checkForUpdate()
        val available = checker.state.value as UpdateState.Available
        // 128MB free on a 4GB TV: 200MB + 60MB needed.
        availableBytes = 128 * UpdateStorage.MB
        checker.downloadAndInstall()

        val state = checker.state.value as UpdateState.InsufficientStorage
        assertEquals(threshold + 3 * apkSize - availableBytes, state.missingBytes)
        assertEquals(available, state.pending)
        assertEquals(1, reclaimCount)
        assertEquals(1, server.requestCount) // no download attempted
    }

    @Test
    fun `freeing caches recovers enough space and the download proceeds`() {
        enqueueRelease(size = 4)
        val checker = checker()
        checker.checkForUpdate()
        availableBytes = threshold // short by the APK sizes only
        onReclaim = { availableBytes += UpdateStorage.MB }
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3, 4))))
        checker.downloadAndInstall()

        assertEquals(1, reclaimCount)
        assertTrue(checker.state.value is UpdateState.ReadyToInstall)
    }

    @Test
    fun `retry after freeing space starts the download`() {
        enqueueRelease(size = 4)
        val checker = checker()
        checker.checkForUpdate()
        availableBytes = 0
        checker.downloadAndInstall()
        assertTrue(checker.state.value is UpdateState.InsufficientStorage)

        checker.retryAfterStorage() // still full
        assertTrue(checker.state.value is UpdateState.InsufficientStorage)
        assertEquals(2, reclaimCount)

        availableBytes = totalBytes / 2
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3, 4))))
        checker.retryAfterStorage()
        assertTrue(checker.state.value is UpdateState.ReadyToInstall)
    }

    @Test
    fun `installApk re-checks space and 重試 resumes the install`() {
        val apkSize = 4L
        enqueueRelease(size = apkSize)
        val checker = checker()
        checker.checkForUpdate()
        // Enough space for the download; something else fills the disk before 安裝.
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3, 4))))
        checker.downloadAndInstall()
        assertTrue(checker.state.value is UpdateState.ReadyToInstall)

        // installApk re-checks.
        availableBytes = threshold
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        checker.installApk(activity)
        val state = checker.state.value as UpdateState.InsufficientStorage
        assertEquals(2 * apkSize, state.missingBytes)
        val pending = state.pending as UpdateState.ReadyToInstall
        assertTrue(pending.file.isFile)
        assertNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)

        // 重試 with space: back to ReadyToInstall with autoLaunch (MainActivity launches it).
        availableBytes = totalBytes / 2
        checker.retryAfterStorage()
        val ready = checker.state.value as UpdateState.ReadyToInstall
        assertTrue(ready.autoLaunch)
        assertEquals(pending.file, ready.file)
    }

    @Test
    fun `space used up by the download is reported before the installer is launched`() {
        enqueueRelease(size = 4)
        val checker = checker()
        checker.checkForUpdate()
        // Plenty before the download; the disk fills up while downloading.
        var reads = 0
        storageReader = { if (reads++ == 0) totalBytes / 2 else threshold + 7 }
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3, 4))))
        checker.downloadAndInstall()

        val state = checker.state.value as UpdateState.InsufficientStorage
        assertEquals(1, state.missingBytes) // threshold + 2 * 4 needed
        assertEquals(1, reclaimCount)
        val pending = state.pending as UpdateState.ReadyToInstall
        assertTrue(pending.file.isFile) // kept for 重試
    }

    @Test
    fun `stale update files are deleted when a check starts`() {
        val dir = tmp.root.resolve("updates").apply { mkdirs() }
        val stale = dir.resolve("update.apk").apply { writeBytes(byteArrayOf(1, 2)) }
        val part = dir.resolve("update.apk.part").apply { writeBytes(byteArrayOf(1)) }
        enqueueRelease(tag = "v1.0.0")
        checker().checkForUpdate()
        assertFalse(stale.exists())
        assertFalse(part.exists())
    }

    @Test
    fun `apk ready to install is not deleted by a check`() {
        enqueueRelease(size = 4)
        val checker = checker()
        checker.checkForUpdate()
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3, 4))))
        checker.downloadAndInstall()
        val ready = checker.state.value as UpdateState.ReadyToInstall

        checker.checkForUpdate() // not Idle: no cleanup
        assertTrue(ready.file.isFile)
    }
}
