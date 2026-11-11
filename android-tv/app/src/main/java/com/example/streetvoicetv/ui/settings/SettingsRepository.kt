package com.example.streetvoicetv.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** 啟動時: what the app does when it starts. */
enum class StartupBehavior(val label: String) {
    SHOW_HOME("顯示首頁"),
    RESUME_LAST("自動繼續上次播放"),
}

/** 環境模式: idle time before the player switches to ambient mode ([timeoutMs] null = never). */
enum class AmbientTimeout(val label: String, val timeoutMs: Long?) {
    SEC_30("30 秒", 30_000L),
    SEC_45("45 秒", 45_000L),
    MIN_2("2 分鐘", 120_000L),
    OFF("關閉", null),
}

/** App preferences of the settings screen (自動播放 lives in PlaybackManager). */
@Immutable
data class AppSettings(
    val startupBehavior: StartupBehavior = StartupBehavior.SHOW_HOME,
    val ambientTimeout: AmbientTimeout = AmbientTimeout.SEC_45,
)

/** Where [AppSettings] are kept (SharedPreferences; in memory in tests). */
interface SettingsStorage {
    fun load(): AppSettings
    fun save(settings: AppSettings)

    class InMemory(private var value: AppSettings = AppSettings()) : SettingsStorage {
        override fun load() = value
        override fun save(settings: AppSettings) {
            value = settings
        }
    }
}

internal class SharedPreferencesSettingsStorage(private val prefs: SharedPreferences) : SettingsStorage {
    override fun load() = AppSettings(
        startupBehavior = enumOrDefault(prefs.getString(KEY_STARTUP, null), StartupBehavior.SHOW_HOME),
        ambientTimeout = enumOrDefault(prefs.getString(KEY_AMBIENT, null), AmbientTimeout.SEC_45),
    )

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_STARTUP, settings.startupBehavior.name)
            .putString(KEY_AMBIENT, settings.ambientTimeout.name)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_STARTUP = "startup_behavior"
        const val KEY_AMBIENT = "ambient_timeout"
    }
}

/** App settings as a [StateFlow]; changes are persisted at once. */
@Singleton
class SettingsRepository internal constructor(private val storage: SettingsStorage) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesSettingsStorage(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
    )

    private val _settings = MutableStateFlow(storage.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setStartupBehavior(behavior: StartupBehavior) = edit { it.copy(startupBehavior = behavior) }

    fun setAmbientTimeout(timeout: AmbientTimeout) = edit { it.copy(ambientTimeout = timeout) }

    private fun edit(transform: (AppSettings) -> AppSettings) {
        val before = _settings.value
        _settings.update(transform)
        val after = _settings.value
        if (after != before) storage.save(after)
    }

    private companion object {
        const val PREFS_NAME = "app_settings"
    }
}
