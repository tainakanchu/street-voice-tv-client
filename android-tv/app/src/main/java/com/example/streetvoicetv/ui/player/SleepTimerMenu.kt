package com.example.streetvoicetv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.streetvoicetv.playback.SleepTimerOption
import com.example.streetvoicetv.playback.SleepTimerState
import com.example.streetvoicetv.playback.formatRemaining
import com.example.streetvoicetv.ui.components.ModalFocusTrap
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.example.streetvoicetv.ui.theme.Dimens

/** Test tag of the sleep timer menu. */
const val SLEEP_TIMER_MENU_TAG = "player_sleep_timer_menu"

/** Test tag of the remaining-time line under the controls. */
const val SLEEP_TIMER_STATUS_TAG = "player_sleep_timer_status"

/** Content description of the sleep timer button in the controls row. */
const val SLEEP_TIMER_BUTTON_DESCRIPTION = "睡眠計時"

/** The option matching the running timer (end of song / off; a countdown matches none). */
private fun SleepTimerState.selectedOption(): SleepTimerOption? = when (this) {
    SleepTimerState.Off -> SleepTimerOption.OFF
    SleepTimerState.EndOfSong -> SleepTimerOption.END_OF_SONG
    is SleepTimerState.Countdown -> null
}

/**
 * Sleep timer choices (15 / 30 / 60 / 90 分, 本曲結束, 關閉) as a modal list in the lower left,
 * above the controls. Focus starts on the active choice (or 30 分), cannot leave the menu, and
 * Back closes it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SleepTimerMenu(
    state: SleepTimerState,
    onSelect: (SleepTimerOption) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val selected = state.selectedOption()
    val initial = selected?.takeIf { it != SleepTimerOption.OFF } ?: SleepTimerOption.MIN_30
    val initialFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .then(ModalFocusTrap),
        contentAlignment = Alignment.BottomStart,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(start = Dimens.SafeHorizontal + 40.dp, bottom = 96.dp)
                .width(300.dp)
                .testTag(SLEEP_TIMER_MENU_TAG),
        ) {
            Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("睡眠計時", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (state is SleepTimerState.Countdown) {
                        Text(
                            "剩 ${formatRemaining(state.remainingMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                SleepTimerOption.entries.forEach { option ->
                    ListItem(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (option == initial) Modifier.focusRequester(initialFocus) else Modifier),
                        scale = ListItemDefaults.scale(focusedScale = Dimens.ListItemFocusedScale),
                        headlineContent = {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        trailingContent = {
                            if (option == selected) Icon(Icons.Default.Check, contentDescription = "已選擇", modifier = Modifier.size(20.dp))
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { initialFocus.requestFocusWhenReady() }
}

/** "🌙 睡眠計時 29:41" / "🌙 本曲結束後暫停" under the controls; recomposes with each tick. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SleepTimerStatus(sleepTimer: () -> SleepTimerState, modifier: Modifier = Modifier) {
    val text = when (val state = sleepTimer()) {
        is SleepTimerState.Countdown -> "睡眠計時 ${formatRemaining(state.remainingMs)} 後暫停"
        SleepTimerState.EndOfSong -> "睡眠計時：本曲結束後暫停"
        SleepTimerState.Off -> return
    }
    Row(
        modifier = modifier.testTag(SLEEP_TIMER_STATUS_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.Bedtime, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.7f))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}
