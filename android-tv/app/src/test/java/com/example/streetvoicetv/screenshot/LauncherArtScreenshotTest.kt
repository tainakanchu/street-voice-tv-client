package com.example.streetvoicetv.screenshot

import android.app.Application
import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.streetvoicetv.R
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real launcher resources (TV banner, adaptive icon, themed-icon
 * monochrome layer) through the framework drawable pipeline, so changes to
 * res/drawable / res/mipmap show up as screenshot diffs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class LauncherArtScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun launcher_art_banner() {
        compose.setContent {
            Box(Modifier.testTag(TAG)) {
                ResourceImage(R.drawable.app_banner, width = 320.dp, height = 180.dp)
            }
        }
        capture("launcher_art_banner")
    }

    @Test
    fun launcher_art_overview() {
        compose.setContent {
            Column(
                modifier = Modifier
                    .testTag(TAG)
                    .background(LauncherBackdrop)
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                // Banner as the launcher shows it: rounded corners applied by the launcher.
                Box(Modifier.clip(RoundedCornerShape(12.dp))) {
                    ResourceImage(R.drawable.app_banner, width = 320.dp, height = 180.dp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Adaptive icons, masked by the platform.
                    ResourceImage(R.mipmap.ic_launcher, 96.dp, 96.dp)
                    ResourceImage(R.mipmap.ic_launcher_round, 96.dp, 96.dp)
                    ResourceImage(R.mipmap.ic_launcher, 48.dp, 48.dp)
                    // Unmasked 108dp canvas: background + foreground layers.
                    Box {
                        ResourceImage(R.drawable.ic_launcher_background, 108.dp, 108.dp)
                        ResourceImage(R.drawable.ic_launcher_foreground, 108.dp, 108.dp)
                    }
                    // Themed icon: monochrome layer tinted as a launcher would.
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B2A2B)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ResourceImage(R.drawable.ic_launcher_monochrome, 144.dp, 144.dp, tint = 0xFFFFB3B3.toInt())
                    }
                }
            }
        }
        capture("launcher_art_overview")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onNodeWithTag(TAG).captureRoboImage("$name.png", roborazziOptions = Options)
    }

    private companion object {
        const val TAG = "launcher_art"
        val LauncherBackdrop = Color(0xFF0E0F11)
        val Options = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
        )
    }
}

/** Shows a drawable/mipmap resource via a framework [ImageView] (handles adaptive icons). */
@Composable
private fun ResourceImage(@DrawableRes res: Int, width: Dp, height: Dp, tint: Int? = null) {
    AndroidView(
        modifier = Modifier.size(width, height),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageResource(res)
                tint?.let { imageTintList = ColorStateList.valueOf(it) }
            }
        },
    )
}
