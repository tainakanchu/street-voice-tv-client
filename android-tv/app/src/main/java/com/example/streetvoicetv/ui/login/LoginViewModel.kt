package com.example.streetvoicetv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.LoginVerifier
import com.example.streetvoicetv.data.auth.SessionCookies
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.auth.parseSessionCookies
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginState {
    /** The user is on the login web page. */
    data object Browsing : LoginState

    /** Cookies were captured; checking `/user/me/` with them. */
    data object Confirming : LoginState

    /** Login confirmed and saved (one-shot: emitted once per ViewModel). */
    data object Success : LoginState

    /** The user pressed Back without a confirmed login. */
    data object Cancelled : LoginState
}

/**
 * Login is only accepted once `/api/v4/user/me/` answers with a username for the captured
 * cookies; only then the session is saved (with that username). Completion is one-shot, so
 * repeated onPageFinished callbacks / Back presses cannot pop the back stack twice.
 */
@HiltViewModel
class LoginViewModel internal constructor(
    private val verify: suspend (SessionCookies) -> Result<String>,
    private val sessionManager: SessionManager,
) : ViewModel() {

    @Inject
    constructor(verifier: LoginVerifier, sessionManager: SessionManager) :
        this(verifier::verify, sessionManager)

    private val _state = MutableStateFlow<LoginState>(LoginState.Browsing)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private var finished = false
    private var confirmJob: Job? = null
    private var cancelAfterConfirm = false

    /** A page other than the login form finished loading: the user may have logged in. */
    fun onPageFinished(cookieString: String?) {
        tryConfirm(parseSessionCookies(cookieString), userLeaving = false)
    }

    /** Back with no web history left: confirm a login if there is one, otherwise leave. */
    fun onBack(cookieString: String?) {
        if (finished) return
        if (confirmJob?.isActive == true) {
            // Let the running check finish; leave afterwards if it fails.
            cancelAfterConfirm = true
            return
        }
        val cookies = parseSessionCookies(cookieString)
        if (cookies == null) {
            finish(LoginState.Cancelled)
        } else {
            tryConfirm(cookies, userLeaving = true)
        }
    }

    private fun tryConfirm(cookies: SessionCookies?, userLeaving: Boolean) {
        if (finished || cookies == null) return
        if (confirmJob?.isActive == true) return
        cancelAfterConfirm = userLeaving
        _state.value = LoginState.Confirming
        confirmJob = viewModelScope.launch {
            val result = verify(cookies)
            if (finished) return@launch
            val username = result.getOrNull()
            when {
                username != null -> {
                    sessionManager.saveSession(
                        sessionId = cookies.sessionId,
                        csrfToken = cookies.csrfToken,
                        username = username,
                        sessionCookieName = cookies.sessionCookieName,
                        csrfCookieName = cookies.csrfCookieName,
                    )
                    finish(LoginState.Success)
                }
                cancelAfterConfirm -> finish(LoginState.Cancelled)
                else -> _state.value = LoginState.Browsing
            }
        }
    }

    private fun finish(state: LoginState) {
        if (finished) return
        finished = true
        _state.value = state
    }
}
