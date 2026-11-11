package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.example.streetvoicetv.ui.theme.Dimens
import kotlinx.coroutines.delay

/** Test tag of the brief message bar. */
const val APP_MESSAGE_TAG = "app_message"

@Immutable
data class AppMessage(val id: Long, val text: String)

/**
 * App-level brief messages (snackbar / toast style: 「已加入佇列：…」). One message at a time; a
 * new one replaces the current one. Screens reach it through [LocalAppMessages].
 */
@Stable
class AppMessageState(
    /** How long a message stays; null keeps it until replaced (screenshots). */
    internal val durationMs: Long? = DEFAULT_DURATION_MS,
) {
    var current: AppMessage? by mutableStateOf(null)
        private set

    private var nextId = 0L

    fun show(text: String) {
        current = AppMessage(nextId++, text)
    }

    fun dismiss(message: AppMessage) {
        if (current?.id == message.id) current = null
    }

    companion object {
        const val DEFAULT_DURATION_MS = 3_000L
    }
}

/** The app's [AppMessageState]; null outside [AppMessageHost] (messages are then dropped). */
val LocalAppMessages = staticCompositionLocalOf<AppMessageState?> { null }

@Composable
fun rememberAppMessageState(): AppMessageState = remember { AppMessageState() }

/**
 * Provides [state] as [LocalAppMessages] to [content] and shows its current message at the
 * bottom start (the NowPlayingBar sits at the bottom end). The bar is not focusable: it never
 * takes focus away from what the user is doing.
 */
@Composable
fun AppMessageHost(state: AppMessageState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppMessages provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            val message = state.current
            if (message != null) {
                state.durationMs?.let { duration ->
                    LaunchedEffect(message.id) {
                        delay(duration)
                        state.dismiss(message)
                    }
                }
                AppMessageBar(
                    text = message.text,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Dimens.SafeHorizontal, bottom = Dimens.SafeVertical),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppMessageBar(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        modifier = modifier
            .widthIn(max = 420.dp)
            .testTag(APP_MESSAGE_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
