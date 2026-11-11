package com.example.streetvoicetv.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Login state as one value, so observers see "logged in" and "username" change together
 * (e.g. a username that becomes available after login triggers a reload).
 */
data class SessionState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
)

/** The session cookie as the site set it (name + value), plus the optional CSRF cookie. */
data class SessionCookies(
    val sessionCookieName: String,
    val sessionId: String,
    val csrfCookieName: String?,
    val csrfToken: String?,
)

@Singleton
class SessionManager internal constructor(
    private val prefs: SharedPreferences,
    private val clearWebCookies: () -> Unit,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ::clearWebViewCookies,
    )

    private val lock = Any()

    private val _session = MutableStateFlow(readState())
    /** Combined login state; collect with distinctUntilChanged-like semantics (StateFlow already conflates equal values). */
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(_session.value.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow(_session.value.username)
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _profileImageUrl = MutableStateFlow(prefs.getString(KEY_PROFILE_IMAGE, null))
    val profileImageUrl: StateFlow<String?> = _profileImageUrl.asStateFlow()

    /**
     * Stores a confirmed session. [csrfToken] blank is stored as "absent".
     * [sessionCookieName] is the cookie name the site used ("session" or "sessionid"), so the
     * same name is sent back.
     */
    fun saveSession(
        sessionId: String,
        csrfToken: String?,
        username: String?,
        sessionCookieName: String = DEFAULT_SESSION_COOKIE,
        csrfCookieName: String? = DEFAULT_CSRF_COOKIE,
    ) {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        val csrf = csrfToken?.takeIf { it.isNotBlank() }
        synchronized(lock) {
            prefs.edit()
                .putString(KEY_SESSION_ID, sessionId)
                .putString(KEY_SESSION_COOKIE_NAME, sessionCookieName)
                .apply {
                    if (csrf != null) {
                        putString(KEY_CSRF_TOKEN, csrf)
                        putString(KEY_CSRF_COOKIE_NAME, csrfCookieName ?: DEFAULT_CSRF_COOKIE)
                    } else {
                        remove(KEY_CSRF_TOKEN)
                        remove(KEY_CSRF_COOKIE_NAME)
                    }
                }
                .putString(KEY_USERNAME, username)
                .apply()
            publish(SessionState(isLoggedIn = true, username = username))
        }
    }

    /** Sets the username of the current session (e.g. after re-fetching /user/me/). */
    fun updateUsername(username: String) {
        synchronized(lock) {
            if (!_session.value.isLoggedIn) return
            prefs.edit().putString(KEY_USERNAME, username).apply()
            publish(_session.value.copy(username = username))
        }
    }

    fun saveProfileImage(url: String?) {
        prefs.edit().putString(KEY_PROFILE_IMAGE, url).apply()
        _profileImageUrl.value = url
    }

    fun clearSession() {
        synchronized(lock) {
            prefs.edit().clear().apply()
            publish(SessionState())
            _profileImageUrl.value = null
        }
        clearWebCookies()
    }

    /**
     * Clears the session only if it is still the one identified by [sessionId], so a late
     * 401 for an old session cannot log out a session created in the meantime.
     * Returns true if the session was cleared.
     */
    fun clearSessionIfCurrent(sessionId: String): Boolean {
        synchronized(lock) {
            if (getSessionId() != sessionId) return false
        }
        clearSession()
        return true
    }

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)?.takeIf { it.isNotBlank() }
    fun getCsrfToken(): String? = prefs.getString(KEY_CSRF_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** The cookies to send to StreetVoice, or null when not logged in. */
    fun getCookies(): SessionCookies? {
        val id = getSessionId() ?: return null
        val csrf = getCsrfToken()
        return SessionCookies(
            sessionCookieName = prefs.getString(KEY_SESSION_COOKIE_NAME, null) ?: DEFAULT_SESSION_COOKIE,
            sessionId = id,
            csrfCookieName = if (csrf != null) {
                prefs.getString(KEY_CSRF_COOKIE_NAME, null) ?: DEFAULT_CSRF_COOKIE
            } else {
                null
            },
            csrfToken = csrf,
        )
    }

    private fun publish(state: SessionState) {
        _session.value = state
        _isLoggedIn.value = state.isLoggedIn
        _username.value = state.username
    }

    private fun readState(): SessionState {
        val loggedIn = getSessionId() != null
        return SessionState(
            isLoggedIn = loggedIn,
            username = if (loggedIn) prefs.getString(KEY_USERNAME, null) else null,
        )
    }

    companion object {
        /** SharedPreferences file holding the session; excluded from backups (res/xml). */
        const val PREFS_NAME = "streetvoice_session"
        const val DEFAULT_SESSION_COOKIE = "session"
        const val DEFAULT_CSRF_COOKIE = "csrf-token"

        private const val KEY_SESSION_ID = "sessionid"
        private const val KEY_SESSION_COOKIE_NAME = "session_cookie_name"
        private const val KEY_CSRF_TOKEN = "csrf_token"
        private const val KEY_CSRF_COOKIE_NAME = "csrf_cookie_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_PROFILE_IMAGE = "profile_image"
    }
}

private fun clearWebViewCookies() {
    try {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    } catch (e: Exception) {
        // WebView may be unavailable (e.g. being updated); the app session is cleared anyway.
    }
}
