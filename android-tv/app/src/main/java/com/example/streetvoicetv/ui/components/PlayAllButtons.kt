package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.example.streetvoicetv.ui.common.PlayAllMode

/**
 * 「播放全部」/「隨機播放」 header buttons of a song list. While [pending] is set (the remaining
 * songs are being loaded before playback starts) that button shows a spinner instead of its
 * icon; both stay focusable so the focus never jumps away (repeated presses are ignored by the
 * ViewModel).
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayAllButtons(
    pending: PlayAllMode?,
    onPlayAll: (PlayAllMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playAllFocus = remember { FocusRequester() }
    Row(
        modifier = modifier
            // Entering the row from the list (Up) or from above (Down) always lands on 播放全部,
            // not on whichever button happens to be geometrically closest.
            .focusProperties { enter = { playAllFocus } }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onPlayAll(PlayAllMode.InOrder) },
            modifier = Modifier.focusRequester(playAllFocus),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            ButtonContent(Icons.Default.PlayArrow, "播放全部", loading = pending == PlayAllMode.InOrder)
        }
        OutlinedButton(
            onClick = { onPlayAll(PlayAllMode.Shuffle) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            ButtonContent(Icons.Default.Shuffle, "隨機播放", loading = pending == PlayAllMode.Shuffle)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ButtonContent(icon: ImageVector, label: String, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(ButtonDefaults.IconSize),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    }
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label)
}
