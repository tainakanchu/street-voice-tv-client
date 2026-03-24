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

    private val _profileImageUrl = MutableStateFlow(prefs.getString(KEY_PROFILE_IMAGE, null))
    val profileImageUrl: StateFlow<String?> = _profileImageUrl.asStateFlow()

    fun saveSession(sessionId: String, csrfToken: String, username: String?) {
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_CSRF_TOKEN, csrfToken)
            .putString(KEY_USERNAME, username)
            .apply()
        _isLoggedIn.value = true
        _username.value = username
    }

    fun saveProfileImage(url: String?) {
        prefs.edit().putString(KEY_PROFILE_IMAGE, url).apply()
        _profileImageUrl.value = url
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
        _username.value = null
        _profileImageUrl.value = null
        // WebView の Cookie もクリア
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)
    fun getCsrfToken(): String? = prefs.getString(KEY_CSRF_TOKEN, null)

    private fun hasSession(): Boolean = getSessionId() != null
    private fun getStoredUsername(): String? = prefs.getString(KEY_USERNAME, null)

    companion object {
        private const val KEY_SESSION_ID = "sessionid"
        private const val KEY_CSRF_TOKEN = "csrf_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_PROFILE_IMAGE = "profile_image"
    }
}
