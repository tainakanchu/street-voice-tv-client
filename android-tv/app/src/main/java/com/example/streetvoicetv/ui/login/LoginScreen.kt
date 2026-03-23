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
            if (tryExtractSession(sessionManager)) {
                onLoginSuccess()
            } else {
                onBack()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "Login to StreetVoice  (press Back when done)",
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
                                if (tryExtractSession(sessionManager)) {
                                    onLoginSuccess()
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

/** Cookie から session を取得して保存。成功したら true */
private fun tryExtractSession(sessionManager: SessionManager): Boolean {
    val cookies = CookieManager.getInstance().getCookie(BASE_URL) ?: return false

    val cookieMap = cookies.split(";")
        .map { it.trim() }
        .filter { it.contains("=") }
        .associate {
            val (key, value) = it.split("=", limit = 2)
            key.trim() to value.trim()
        }

    // StreetVoice の session cookie は "session" という名前
    val sessionId = cookieMap["session"] ?: cookieMap["sessionid"] ?: return false
    val csrfToken = cookieMap["csrf-token"] ?: ""

    sessionManager.saveSession(
        sessionId = sessionId,
        csrfToken = csrfToken,
        username = null,
    )
    return true
}
