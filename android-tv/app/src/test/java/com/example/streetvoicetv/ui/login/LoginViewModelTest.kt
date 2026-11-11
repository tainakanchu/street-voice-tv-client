package com.example.streetvoicetv.ui.login

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.data.auth.SessionCookies
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.auth.SessionState
import com.example.streetvoicetv.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("login_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    private val session = SessionManager(prefs) {}

    private val verified = mutableListOf<SessionCookies>()
    private var verifyResult: CompletableDeferred<Result<String>> = CompletableDeferred()

    private val vm = LoginViewModel(
        verify = { cookies -> verified += cookies; verifyResult.await() },
        sessionManager = session,
    )

    @Test
    fun `login is saved only after user me confirms, with username and cookie name`() {
        vm.onPageFinished("sessionid=abc; csrftoken=t")
        assertEquals(LoginState.Confirming, vm.state.value)
        assertEquals(SessionState(), session.session.value) // nothing saved yet

        verifyResult.complete(Result.success("alice"))

        assertEquals(LoginState.Success, vm.state.value)
        assertEquals(SessionState(true, "alice"), session.session.value)
        assertEquals("sessionid", session.getCookies()!!.sessionCookieName)
    }

    @Test
    fun `repeated page finishes confirm once and succeed once`() {
        vm.onPageFinished("session=abc")
        vm.onPageFinished("session=abc")
        vm.onBack("session=abc")
        assertEquals(1, verified.size)
        verifyResult.complete(Result.success("alice"))
        assertEquals(LoginState.Success, vm.state.value)

        // Later callbacks after success are ignored.
        vm.onPageFinished("session=abc")
        vm.onBack("session=abc")
        assertEquals(1, verified.size)
        assertEquals(LoginState.Success, vm.state.value)
    }

    @Test
    fun `failed confirmation saves nothing and the user can retry`() {
        vm.onPageFinished("session=anonymous")
        verifyResult.complete(Result.failure(RuntimeException("403")))
        assertEquals(LoginState.Browsing, vm.state.value)
        assertNull(session.getSessionId())

        verifyResult = CompletableDeferred()
        vm.onPageFinished("session=real")
        verifyResult.complete(Result.success("bob"))
        assertEquals(LoginState.Success, vm.state.value)
        assertEquals("real", session.getSessionId())
    }

    @Test
    fun `back without a session cancels, back with an unconfirmed session cancels after check`() {
        vm.onBack(null)
        assertEquals(LoginState.Cancelled, vm.state.value)

        val vm2 = LoginViewModel({ Result.failure(RuntimeException("no")) }, session)
        vm2.onBack("session=anonymous")
        assertEquals(LoginState.Cancelled, vm2.state.value)
        assertNull(session.getSessionId())
    }
}
