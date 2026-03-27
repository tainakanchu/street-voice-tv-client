package com.example.streetvoicetv.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.streetvoicetv.data.auth.SessionManager

private const val LOGIN_URL = "https://streetvoice.com/accounts/login/"
private const val BASE_URL = "https://streetvoice.com"

@OptIn(ExperimentalTvMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Back ボタンで cookie チェック → ログイン成功 or 戻る
    BackHandler {
        // WebView 内で戻れるページがあればまず戻る
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            // WebView の履歴がなくなったら cookie チェックして終了
            if (saveCookieSession(sessionManager)) {
                // JS でユーザー名を取得してからコールバック
                fetchUsernameViaJs(wv, sessionManager, onLoginSuccess)
            } else {
                onBack()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "登入 StreetVoice（完成後請按返回鍵）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
        )

        if (isLoading) {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
            )
        }

        // WebView
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                    val cookieMgr = CookieManager.getInstance()
                    cookieMgr.setAcceptCookie(true)
                    cookieMgr.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            // ログイン後にページ遷移した場合は自動検知
                            if (url != null && !url.contains("/accounts/login")) {
                                if (saveCookieSession(sessionManager)) {
                                    fetchUsernameViaJs(view, sessionManager, onLoginSuccess)
                                }
                            }
                        }
                    }

                    webViewRef = this
                    loadUrl(LOGIN_URL)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Cookie から session を取得して保存（username は後で JS から取得）。成功したら true */
private fun saveCookieSession(sessionManager: SessionManager): Boolean {
    val cookies = CookieManager.getInstance().getCookie(BASE_URL) ?: return false

    val cookieMap = cookies.split(";")
        .map { it.trim() }
        .filter { it.contains("=") }
        .associate {
            val (key, value) = it.split("=", limit = 2)
            key.trim() to value.trim()
        }

    val sessionId = cookieMap["session"] ?: cookieMap["sessionid"] ?: return false
    val csrfToken = cookieMap["csrf-token"] ?: ""

    sessionManager.saveSession(
        sessionId = sessionId,
        csrfToken = csrfToken,
        username = sessionManager.username.value, // 既存のユーザー名を維持
    )
    return true
}

/** WebView 内の JS で /api/v4/user/me/ を叩いてユーザー名を取得 */
private fun fetchUsernameViaJs(webView: WebView?, sessionManager: SessionManager, onDone: () -> Unit) {
    if (webView == null) {
        onDone()
        return
    }

    val js = """
        (function() {
            try {
                var xhr = new XMLHttpRequest();
                xhr.open('GET', '/api/v4/user/me/', false);
                xhr.setRequestHeader('Accept', 'application/json');
                xhr.send();
                if (xhr.status === 200) {
                    var data = JSON.parse(xhr.responseText);
                    return data.username || '';
                }
            } catch(e) {}
            return '';
        })();
    """.trimIndent()

    webView.evaluateJavascript(js) { result ->
        val username = result?.trim('"')?.takeIf { it.isNotBlank() }
        if (username != null) {
            sessionManager.saveSession(
                sessionId = sessionManager.getSessionId() ?: "",
                csrfToken = sessionManager.getCsrfToken() ?: "",
                username = username,
            )
        }
        onDone()
    }
}
