package com.example.streetvoicetv.ui.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.streetvoicetv.BuildConfig
import com.example.streetvoicetv.data.SearchHistoryManager
import com.example.streetvoicetv.data.update.UpdateCheckResult
import com.example.streetvoicetv.data.update.UpdateChecker
import com.example.streetvoicetv.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The 清除… actions of the settings screen (each asks for confirmation first). */
enum class ClearAction(val title: String, val description: String, val confirmMessage: String) {
    SEARCH_HISTORY("清除搜尋紀錄", "刪除最近的搜尋關鍵字", "最近的搜尋關鍵字將被刪除。"),
    IMAGE_CACHE("清除圖片快取", "釋放封面與頭像佔用的空間", "已下載的圖片將被刪除，之後會重新下載。"),
    RECENTLY_PLAYED("清除最近播放", "清空首頁的最近播放清單", "最近播放的歌曲清單將被清空。"),
}

/** 檢查更新 progress / outcome shown under the row. */
sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data class Done(val result: UpdateCheckResult) : UpdateCheckStatus
}

@Immutable
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val autoplayEnabled: Boolean = true,
    val versionName: String = "",
    val cleared: Set<ClearAction> = emptySet(),
    val updateCheck: UpdateCheckStatus = UpdateCheckStatus.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val playbackManager: PlaybackManager,
    private val searchHistoryManager: SearchHistoryManager,
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val cleared = MutableStateFlow<Set<ClearAction>>(emptySet())
    private val updateCheck = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
    private var updateJob: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        playbackManager.autoplayEnabled,
        cleared,
        updateCheck,
    ) { settings, autoplay, cleared, update ->
        SettingsUiState(settings, autoplay, BuildConfig.VERSION_NAME, cleared, update)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsUiState(
            settings = settingsRepository.settings.value,
            autoplayEnabled = playbackManager.autoplayEnabled.value,
            versionName = BuildConfig.VERSION_NAME,
        ),
    )

    fun cycleStartupBehavior() {
        val current = settingsRepository.settings.value.startupBehavior
        settingsRepository.setStartupBehavior(current.next())
    }

    fun cycleAmbientTimeout() {
        val current = settingsRepository.settings.value.ambientTimeout
        settingsRepository.setAmbientTimeout(current.next())
    }

    fun toggleAutoplay() = playbackManager.toggleAutoplay()

    @OptIn(ExperimentalCoilApi::class)
    fun clear(action: ClearAction) {
        when (action) {
            ClearAction.SEARCH_HISTORY ->
                searchHistoryManager.history.value.forEach(searchHistoryManager::remove)
            ClearAction.RECENTLY_PLAYED -> playbackManager.clearRecentlyPlayed()
            ClearAction.IMAGE_CACHE -> {
                val loader = context.imageLoader
                loader.memoryCache?.clear()
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            loader.diskCache?.clear()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        cleared.value = cleared.value + action
    }

    fun checkForUpdate() {
        if (updateJob?.isActive == true) return
        updateCheck.value = UpdateCheckStatus.Checking
        updateJob = viewModelScope.launch {
            updateCheck.value = UpdateCheckStatus.Done(updateChecker.checkNow())
        }
    }
}

internal fun <E : Enum<E>> E.nextIn(values: Array<E>): E = values[(ordinal + 1) % values.size]

internal fun StartupBehavior.next(): StartupBehavior = nextIn(StartupBehavior.entries.toTypedArray())

internal fun AmbientTimeout.next(): AmbientTimeout = nextIn(AmbientTimeout.entries.toTypedArray())
