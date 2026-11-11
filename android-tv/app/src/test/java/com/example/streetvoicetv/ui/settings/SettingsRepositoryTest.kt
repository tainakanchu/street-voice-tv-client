package com.example.streetvoicetv.ui.settings

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class SettingsRepositoryTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `defaults are show home and 45 s ambient mode`() {
        val repo = SettingsRepository(context)
        assertEquals(AppSettings(StartupBehavior.SHOW_HOME, AmbientTimeout.SEC_45), repo.settings.value)
        assertEquals(45_000L, repo.settings.value.ambientTimeout.timeoutMs)
    }

    @Test
    fun `changes update the flow and persist`() {
        val repo = SettingsRepository(context)
        repo.setStartupBehavior(StartupBehavior.RESUME_LAST)
        repo.setAmbientTimeout(AmbientTimeout.OFF)
        assertEquals(AppSettings(StartupBehavior.RESUME_LAST, AmbientTimeout.OFF), repo.settings.value)

        val reloaded = SettingsRepository(context)
        assertEquals(AppSettings(StartupBehavior.RESUME_LAST, AmbientTimeout.OFF), reloaded.settings.value)
    }

    @Test
    fun `unknown stored values fall back to the defaults`() {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
            .putString("startup_behavior", "SOMETHING_NEW")
            .putString("ambient_timeout", "MIN_99")
            .commit()
        assertEquals(AppSettings(), SettingsRepository(context).settings.value)
    }

    @Test
    fun `unchanged values are not written again`() {
        var saves = 0
        val storage = object : SettingsStorage {
            var value = AppSettings()
            override fun load() = value
            override fun save(settings: AppSettings) {
                saves++
                value = settings
            }
        }
        val repo = SettingsRepository(storage)
        repo.setAmbientTimeout(AmbientTimeout.SEC_45)
        assertEquals(0, saves)
        repo.setAmbientTimeout(AmbientTimeout.MIN_2)
        assertEquals(1, saves)
    }

    @Test
    fun `choices cycle in order`() {
        assertEquals(StartupBehavior.RESUME_LAST, StartupBehavior.SHOW_HOME.next())
        assertEquals(StartupBehavior.SHOW_HOME, StartupBehavior.RESUME_LAST.next())
        assertEquals(
            listOf(AmbientTimeout.SEC_45, AmbientTimeout.MIN_2, AmbientTimeout.OFF, AmbientTimeout.SEC_30),
            AmbientTimeout.entries.map { it.next() },
        )
        assertEquals(listOf(30_000L, 45_000L, 120_000L, null), AmbientTimeout.entries.map { it.timeoutMs })
    }
}
