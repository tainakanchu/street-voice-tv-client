package com.example.streetvoicetv.ui.settings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.example.streetvoicetv.data.update.UpdateCheckResult
import com.example.streetvoicetv.ui.components.BlockFocusEntry
import com.example.streetvoicetv.ui.components.ConfirmDialog
import com.example.streetvoicetv.ui.components.ModalFocusTrap
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset

/** Test tag of the settings list. */
const val SETTINGS_LIST_TAG = "settings_list"

/** Test tag of the 關於 / 授權 dialog. */
const val SETTINGS_ABOUT_TAG = "settings_about"

/** Row titles (also what D-pad tests look for). */
object SettingsLabels {
    const val STARTUP = "啟動時"
    const val AUTOPLAY = "自動播放"
    const val AMBIENT = "環境模式"
    const val CHECK_UPDATE = "檢查更新"
    const val ABOUT = "關於 / 授權"
}

/** A dialog of the settings screen. */
sealed interface SettingsDialog {
    data class Confirm(val action: ClearAction) : SettingsDialog
    data object About : SettingsDialog
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onCycleStartup = viewModel::cycleStartupBehavior,
        onToggleAutoplay = viewModel::toggleAutoplay,
        onCycleAmbient = viewModel::cycleAmbientTimeout,
        onClear = viewModel::clear,
        onCheckUpdate = viewModel::checkForUpdate,
    )
}

/**
 * Stateless settings list. OK on a choice row (啟動時 / 環境模式) moves to the next choice,
 * on 自動播放 toggles it; the 清除 rows ask for confirmation first ([ConfirmDialog], focus on
 * 取消). When a dialog closes, focus returns to the row that opened it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onCycleStartup: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onCycleAmbient: () -> Unit,
    onClear: (ClearAction) -> Unit,
    onCheckUpdate: () -> Unit,
    initialDialog: SettingsDialog? = null,
) {
    var dialog by remember { mutableStateOf(initialDialog) }
    var refocus by remember { mutableStateOf<FocusRequester?>(null) }
    val firstFocus = remember { FocusRequester() }
    val clearFocus = remember { ClearAction.entries.associateWith { FocusRequester() } }
    val aboutFocus = remember { FocusRequester() }

    fun closeDialog(returnTo: FocusRequester) {
        dialog = null
        refocus = returnTo
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (dialog != null) BlockFocusEntry else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Dimens.SafeHorizontal,
                    end = Dimens.SafeHorizontal,
                    top = Dimens.SafeVertical,
                    bottom = Dimens.SafeVertical + LocalBottomOverlayInset.current,
                )
                .testTag(SETTINGS_LIST_TAG),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text("設定", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.widthIn(max = 720.dp)) {
                SectionTitle("播放")
                SettingsRow(
                    icon = Icons.Default.Tv,
                    title = SettingsLabels.STARTUP,
                    description = "開啟應用程式時要做的事",
                    value = state.settings.startupBehavior.label,
                    onClick = onCycleStartup,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = SettingsLabels.AUTOPLAY,
                    description = "播放清單結束後，繼續播放相似的歌曲",
                    onClick = onToggleAutoplay,
                    trailing = { Switch(checked = state.autoplayEnabled, onCheckedChange = null) },
                )
                SettingsRow(
                    icon = Icons.Default.PlayCircleOutline,
                    title = SettingsLabels.AMBIENT,
                    description = "播放畫面閒置多久後切換為環境模式",
                    value = state.settings.ambientTimeout.label,
                    onClick = onCycleAmbient,
                )

                SectionTitle("資料")
                ClearAction.entries.forEach { action ->
                    SettingsRow(
                        icon = when (action) {
                            ClearAction.SEARCH_HISTORY -> Icons.Default.SearchOff
                            ClearAction.IMAGE_CACHE -> Icons.Default.Image
                            ClearAction.RECENTLY_PLAYED -> Icons.Default.History
                        },
                        title = action.title,
                        description = if (action in state.cleared) "已清除" else action.description,
                        onClick = { dialog = SettingsDialog.Confirm(action) },
                        trailing = {
                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.focusRequester(clearFocus.getValue(action)),
                    )
                }

                SectionTitle("關於")
                SettingsRow(
                    icon = Icons.Default.SystemUpdate,
                    title = SettingsLabels.CHECK_UPDATE,
                    description = updateDescription(state),
                    value = "版本 ${state.versionName}",
                    onClick = onCheckUpdate,
                )
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = SettingsLabels.ABOUT,
                    description = "非官方用戶端・開放原始碼授權",
                    onClick = { dialog = SettingsDialog.About },
                    modifier = Modifier.focusRequester(aboutFocus),
                )
            }
        }

        when (val current = dialog) {
            is SettingsDialog.Confirm -> ConfirmDialog(
                title = "${current.action.title}？",
                message = current.action.confirmMessage,
                confirmLabel = "清除",
                dismissLabel = "取消",
                onConfirm = {
                    onClear(current.action)
                    closeDialog(clearFocus.getValue(current.action))
                },
                onDismiss = { closeDialog(clearFocus.getValue(current.action)) },
            )
            SettingsDialog.About -> AboutDialog(onDismiss = { closeDialog(aboutFocus) })
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        if (initialDialog == null) firstFocus.requestFocusWhenReady()
    }
    refocus?.let { target ->
        LaunchedEffect(target) {
            target.requestFocusWhenReady()
            refocus = null
        }
    }
}

private fun updateDescription(state: SettingsUiState): String = when (val check = state.updateCheck) {
    UpdateCheckStatus.Idle -> "從 GitHub 檢查是否有新版本"
    UpdateCheckStatus.Checking -> "檢查中…"
    is UpdateCheckStatus.Done -> when (val result = check.result) {
        UpdateCheckResult.UpToDate -> "已是最新版本"
        is UpdateCheckResult.Available -> "有新版本 ${result.version}"
        UpdateCheckResult.Failed -> "無法檢查更新，請稍後再試"
        UpdateCheckResult.Busy -> "更新進行中"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        scale = ListItemDefaults.scale(focusedScale = Dimens.ListItemFocusedScale),
        leadingContent = { Icon(icon, null, modifier = Modifier.size(ListItemDefaults.IconSize)) },
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { Text(description, maxLines = 1) },
        trailingContent = when {
            trailing != null -> trailing
            value != null -> {
                { Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
            }
            else -> null
        },
    )
}

/** 關於 / 授權: a short text with one 關閉 button (Back closes it too). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .then(ModalFocusTrap),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(520.dp).testTag(SETTINGS_ABOUT_TAG)) {
            Column(Modifier.padding(32.dp)) {
                Text("關於 StreetVoice TV", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                ABOUT_PARAGRAPHS.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(closeFocus)) { Text("關閉") }
            }
        }
    }
    LaunchedEffect(Unit) { closeFocus.requestFocusWhenReady() }
}

private val ABOUT_PARAGRAPHS = listOf(
    "本應用程式為非官方的 StreetVoice 街聲 Android TV 用戶端，與 StreetVoice 及其營運公司無任何關聯。歌曲與圖片的著作權屬於各創作者。",
    "原始碼以 MIT 授權條款釋出。",
    "標誌使用的 Inter 字型採用 SIL Open Font License 1.1 授權。",
)
