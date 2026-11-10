package com.example.streetvoicetv.screenshot

import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.example.streetvoicetv.data.update.UpdateState
import com.example.streetvoicetv.AppScaffold
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.ui.theme.StreetVoiceTvTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.robolectric.RuntimeEnvironment

/**
 * Robolectric qualifiers for a 1080p Android TV: 960x540dp @ xhdpi (2x) = 1920x1080px.
 */
const val TV_1080P = "w960dp-h540dp-land-television-xhdpi"

/** Robolectric SDK used for screenshot tests. */
const val SCREENSHOT_SDK = 35

/**
 * Installs a Coil [ImageLoader] that never touches the network: every request
 * immediately succeeds with a solid colour derived from the URL, so image
 * slots are visible (and deterministic) in screenshots.
 */
fun installFakeImageLoader() {
    val context = RuntimeEnvironment.getApplication()
    val loader = ImageLoader.Builder(context)
        .dispatcher(Dispatchers.Unconfined)
        .components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    val key = chain.request.data.toString()
                    return SuccessResult(
                        drawable = ColorDrawable(placeholderColor(key)),
                        request = chain.request,
                        dataSource = DataSource.MEMORY,
                    )
                }
            })
        }
        .build()
    Coil.setImageLoader(loader)
}

private val PlaceholderPalette = intArrayOf(
    0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFFFA726.toInt(), 0xFF8D6E63.toInt(),
    0xFFAB47BC.toInt(), 0xFF42A5F5.toInt(), 0xFF9CCC65.toInt(), 0xFFEC407A.toInt(),
)

private fun placeholderColor(key: String): Int =
    PlaceholderPalette[(key.hashCode() and 0x7fffffff) % PlaceholderPalette.size]

/**
 * Renders [content] inside the real app chrome ([AppScaffold] + theme) and
 * writes `app/src/test/screenshots/<name>.png`.
 */
fun ComposeContentTestRule.captureTv(
    name: String,
    nowPlaying: PlaybackState = PlaybackState(),
    updateState: UpdateState = UpdateState.Idle,
    beforeCapture: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    setContent {
        TvChrome(nowPlaying = nowPlaying, updateState = updateState, content = content)
    }
    waitForIdle()
    beforeCapture()
    waitForIdle()
    captureTvImage(name)
}

/** Captures the whole window as `app/src/test/screenshots/<name>.png`. */
fun ComposeContentTestRule.captureTvImage(name: String) {
    onRoot().captureRoboImage("$name.png", roborazziOptions = TvRoborazziOptions)
}

/**
 * The real app chrome ([AppScaffold] + theme) around [content], as in MainActivity.
 * The NowPlayingBar is shown whenever [nowPlaying] has media.
 */
@Composable
fun TvChrome(
    nowPlaying: PlaybackState = PlaybackState(),
    updateState: UpdateState = UpdateState.Idle,
    onNowPlayingTap: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    StreetVoiceTvTheme {
        AppScaffold(
            playbackState = nowPlaying,
            showNowPlaying = nowPlaying.hasMedia,
            onNowPlayingTap = onNowPlayingTap,
            onTogglePlayPause = onTogglePlayPause,
            updateState = updateState,
            onUpdate = onUpdate,
            onDismissUpdate = onDismissUpdate,
            content = content,
        )
    }
}

/**
 * Tolerate tiny anti-aliasing differences between machines (0.1% of pixels);
 * real layout changes (overflow, clipping) change far more than that.
 */
private val TvRoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
)
