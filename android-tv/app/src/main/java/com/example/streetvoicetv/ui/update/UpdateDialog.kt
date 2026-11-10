package com.example.streetvoicetv.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.streetvoicetv.data.update.UpdateState
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.components.ModalFocusTrap
import com.example.streetvoicetv.ui.components.requestFocusWhenReady

/** Test tag of the dialog body (focused itself while downloading / installing). */
const val UPDATE_DIALOG_BODY_TAG = "update_dialog_body"

/** Shortens long version strings, e.g. "1.2.3-beta+build.2026" -> "1.2.3-beta". */
internal fun displayVersion(version: String): String {
    val v = version.substringBefore('+')
    return if (v.length > 24) v.take(23) + "…" else v
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is UpdateState.Idle) return

    val primaryFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    val isBusy = state is UpdateState.Downloading || state is UpdateState.ReadyToInstall
    val hasButton = state is UpdateState.Available || state is UpdateState.Error

    // Back dismisses the dialog unless a download / install is in progress
    // (in which case Back is swallowed so it does not reach the screen below).
    BackHandler { if (!isBusy) onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .then(ModalFocusTrap),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(440.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UPDATE_DIALOG_BODY_TAG)
                    // Without buttons (downloading / installing) the dialog body itself
                    // holds focus, so D-pad input never reaches the screen below.
                    .then(if (hasButton) Modifier else Modifier.focusRequester(bodyFocus).focusable())
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    is UpdateState.Available -> {
                        DialogTitle("有新版本可用")
                        Spacer(Modifier.height(12.dp))
                        DialogMessage("v${displayVersion(state.version)} 已可使用")
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss) { Text("稍後") }
                            Button(
                                onClick = onUpdate,
                                modifier = Modifier.focusRequester(primaryFocus),
                            ) { Text("更新") }
                        }
                    }

                    is UpdateState.Downloading -> {
                        DialogTitle("下載中...")
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        DialogMessage("${(state.progress * 100).toInt()}%")
                    }

                    is UpdateState.ReadyToInstall -> {
                        DialogTitle("準備安裝")
                        Spacer(Modifier.height(12.dp))
                        DialogMessage("下載完成，正在啟動安裝程式...")
                    }

                    is UpdateState.Error -> {
                        // Raw details are logged by UpdateChecker; show a fixed message here.
                        DialogTitle("更新失敗", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        DialogMessage(UserMessages.UPDATE_FAILED)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.focusRequester(primaryFocus),
                        ) { Text("關閉") }
                    }

                    else -> {}
                }
            }
        }
    }

    LaunchedEffect(state::class) {
        if (hasButton) primaryFocus.requestFocusWhenReady() else bodyFocus.requestFocusWhenReady()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DialogTitle(text: String, color: Color = Color.Unspecified) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DialogMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
}
