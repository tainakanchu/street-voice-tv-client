package com.example.streetvoicetv.data.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("streetvoice_session", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(hasSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow(getStoredUsername())
    val username: StateFlow<String?> = _username.asStateFlow()

    fun saveSession(sessionId: String, csrfToken: String, username: String?) {
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_CSRF_TOKEN, csrfToken)
            .putString(KEY_USERNAME, username)
            .apply()
        _isLoggedIn.value = true
        _username.value = username
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
        _username.value = null
    }

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)
    fun getCsrfToken(): String? = prefs.getString(KEY_CSRF_TOKEN, null)

    private fun hasSession(): Boolean = getSessionId() != null
    private fun getStoredUsername(): String? = prefs.getString(KEY_USERNAME, null)

    companion object {
        private const val KEY_SESSION_ID = "sessionid"
        private const val KEY_CSRF_TOKEN = "csrf_token"
        private const val KEY_USERNAME = "username"
    }
}
