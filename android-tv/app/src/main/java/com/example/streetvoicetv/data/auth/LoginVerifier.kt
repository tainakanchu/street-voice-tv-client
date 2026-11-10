package com.example.streetvoicetv.data.auth

import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.data.repository.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Confirms that cookies captured from the login WebView belong to a logged-in user by asking
 * `/api/v4/user/me/` with exactly those cookies. A session cookie alone is not proof of login
 * (anonymous visitors get one too).
 */
@Singleton
class LoginVerifier @Inject constructor(
    private val api: StreetVoiceApi,
) {
    /** The username on success. */
    suspend fun verify(cookies: SessionCookies): Result<String> = runCatchingCancellable {
        api.getCurrentUser(cookies).username.also {
            require(it.isNotBlank()) { "blank username" }
        }
    }
}
