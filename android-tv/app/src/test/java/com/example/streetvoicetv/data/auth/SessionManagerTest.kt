package com.example.streetvoicetv.data.auth

import android.app.Application
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SessionManagerTest {

    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("session_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    private fun manager() = SessionManager(prefs) {}

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `combined session flow emits login, username and logout`() = runTest {
        val sm = manager()
        val seen = mutableListOf<SessionState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { sm.session.toList(seen) }

        sm.saveSession("sid", "csrf", username = null)
        sm.updateUsername("alice")
        sm.updateUsername("alice") // no duplicate emission
        sm.clearSession()
        job.cancel()

        assertEquals(
            listOf(
                SessionState(false, null),
                SessionState(true, null),
                SessionState(true, "alice"),
                SessionState(false, null),
            ),
            seen,
        )
        // Legacy flows stay in sync.
        assertEquals(false, sm.isLoggedIn.value)
        assertNull(sm.username.value)
    }

    @Test
    fun `updateUsername is ignored when logged out`() {
        val sm = manager()
        sm.updateUsername("ghost")
        assertEquals(SessionState(), sm.session.value)
    }

    @Test
    fun `cookie names persist across instances and default to session`() {
        manager().saveSession("sid", "tok", "bob", sessionCookieName = "sessionid", csrfCookieName = "csrftoken")
        val restored = manager()
        assertEquals(SessionState(true, "bob"), restored.session.value)
        assertEquals(SessionCookies("sessionid", "sid", "csrftoken", "tok"), restored.getCookies())

        restored.saveSession("sid2", null, "bob")
        assertEquals(SessionCookies("session", "sid2", null, null), restored.getCookies())
    }

    @Test
    fun `parseSessionCookies keeps the real cookie names`() {
        assertEquals(
            SessionCookies("sessionid", "abc", "csrftoken", "t=1"),
            parseSessionCookies("foo=bar; sessionid=abc; csrftoken=t=1"),
        )
        assertEquals(
            SessionCookies("session", "s", "csrf-token", "c"),
            parseSessionCookies("session=s;csrf-token=c"),
        )
        assertEquals(SessionCookies("session", "s", null, null), parseSessionCookies("session=s; csrf-token="))
        assertNull(parseSessionCookies("session=; other=1"))
        assertNull(parseSessionCookies(null))
    }
}
