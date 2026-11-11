package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.example.streetvoicetv.ui.theme.Dimens

/** A button shown below a [MessageState]. */
data class MessageAction(val label: String, val onClick: () -> Unit)

/**
 * Centered, width-limited message used for empty / error states.
 * Long messages are wrapped (max 3 lines) and never leave the safe area.
 * The [primaryAction] (e.g. 重試) receives focus when the state appears.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MessageState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    isError: Boolean = false,
    primaryAction: MessageAction? = null,
    secondaryAction: MessageAction? = null,
    requestInitialFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.SafeHorizontal, vertical = Dimens.SafeVertical),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isError && title == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (primaryAction != null || secondaryAction != null) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (primaryAction != null) {
                        Button(
                            onClick = primaryAction.onClick,
                            modifier = Modifier.focusRequester(focusRequester),
                        ) { Text(primaryAction.label) }
                    }
                    if (secondaryAction != null) {
                        OutlinedButton(onClick = secondaryAction.onClick) { Text(secondaryAction.label) }
                    }
                }
            }
        }
    }

    if (primaryAction != null && requestInitialFocus) {
        LaunchedEffect(message) { focusRequester.requestFocusWhenReady() }
    }
}

/** Requests focus, ignoring the IllegalStateException thrown when the target is not attached. */
fun FocusRequester.tryRequestFocus(): Boolean =
    try {
        requestFocus()
        true
    } catch (_: IllegalStateException) {
        false
    }

/**
 * Requests focus once the target is attached and ready. Items of lazy lists are
 * composed during layout, i.e. possibly after a LaunchedEffect of the parent
 * starts, so this retries for a few frames. Never throws (e.g. empty lists).
 * Returns whether focus was requested successfully.
 */
suspend fun FocusRequester.requestFocusWhenReady(maxFrames: Int = 10): Boolean {
    repeat(maxFrames) {
        // Wait one frame first: tv-material components observe focus through an
        // InteractionSource collected in their own effects. Focusing before those
        // collectors run would leave the element focused but drawn unfocused.
        withFrameNanos { }
        if (tryRequestFocus()) return true
    }
    return false
}
