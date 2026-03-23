package com.example.streetvoicetv.data.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionCookieInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val sessionId = sessionManager.getSessionId()
        val csrfToken = sessionManager.getCsrfToken()

        if (sessionId == null) {
            return chain.proceed(original)
        }

        val cookies = buildString {
            append("session=$sessionId")
            if (csrfToken != null) {
                append("; csrf-token=$csrfToken")
            }
        }

        val request = original.newBuilder()
            .header("Cookie", cookies)
            .apply {
                // POST リクエストには CSRF token をヘッダーにも付与
                if (original.method == "POST" && csrfToken != null) {
                    header("X-CSRFToken", csrfToken)
                }
            }
            .build()

        return chain.proceed(request)
    }
}
