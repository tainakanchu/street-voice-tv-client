package com.example.streetvoicetv.data.auth

import com.example.streetvoicetv.data.api.ApiConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds the stored StreetVoice session cookie (under the name the site used) and, for unsafe
 * methods (anything but GET/HEAD/OPTIONS/TRACE, e.g. POST and DELETE), the X-CSRFToken header.
 *
 * - Only requests to the StreetVoice host get the cookie: the shared OkHttpClient is also used
 *   for update checks / APK downloads from GitHub, which must never see the session.
 * - A request that already carries a Cookie header (login confirmation with freshly captured
 *   cookies) is left unchanged.
 */
@Singleton
class SessionCookieInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    config: ApiConfig,
) : Interceptor {

    private val apiHost: String? = config.baseUrl.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!isApiHost(original.url.host) || original.header("Cookie") != null) {
            return chain.proceed(original)
        }

        val cookies = sessionManager.getCookies() ?: return chain.proceed(original)

        val request = original.newBuilder()
            .header("Cookie", cookies.toCookieHeader())
            .apply {
                if (original.method.uppercase() !in SAFE_METHODS && cookies.csrfToken != null) {
                    header("X-CSRFToken", cookies.csrfToken)
                }
            }
            .build()

        return chain.proceed(request)
    }

    internal fun isApiHost(host: String): Boolean =
        apiHost != null && (host == apiHost || host.endsWith(".$apiHost"))

    private companion object {
        val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")
    }
}

/** `name=value[; csrfName=csrf]` for a Cookie header. */
fun SessionCookies.toCookieHeader(): String = buildString {
    append(sessionCookieName).append('=').append(sessionId)
    if (csrfToken != null && csrfCookieName != null) {
        append("; ").append(csrfCookieName).append('=').append(csrfToken)
    }
}

/** Session cookie names StreetVoice is known to use, in order of preference. */
internal val SESSION_COOKIE_NAMES = listOf("session", "sessionid")

/** CSRF cookie names (StreetVoice's own and Django's default). */
internal val CSRF_COOKIE_NAMES = listOf("csrf-token", "csrftoken")

/**
 * Parses a `Cookie`-style string (as returned by WebView's CookieManager.getCookie) into the
 * session cookies, keeping the actual cookie names. Null when there is no non-blank session.
 */
fun parseSessionCookies(cookieString: String?): SessionCookies? {
    if (cookieString.isNullOrBlank()) return null
    val map = cookieString.split(";")
        .map { it.trim() }
        .filter { '=' in it }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    val sessionName = SESSION_COOKIE_NAMES.firstOrNull { !map[it].isNullOrBlank() } ?: return null
    val csrfName = CSRF_COOKIE_NAMES.firstOrNull { !map[it].isNullOrBlank() }
    return SessionCookies(
        sessionCookieName = sessionName,
        sessionId = map.getValue(sessionName),
        csrfCookieName = csrfName,
        csrfToken = csrfName?.let { map.getValue(it) },
    )
}
