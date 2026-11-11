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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.streetvoicetv.ui.theme.Dimens

private const val LOGIN_URL = "https://streetvoice.com/accounts/login/"
private const val BASE_URL = "https://streetvoice.com"

@OptIn(ExperimentalTvMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    var isPageLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val loginState by viewModel.state.collectAsStateWithLifecycle()

    // Success / Cancelled are terminal and emitted once by the ViewModel, so the back stack
    // is popped exactly once however often pages finish loading.
    LaunchedEffect(loginState) {
        when (loginState) {
            LoginState.Success -> onLoginSuccess()
            LoginState.Cancelled -> onBack()
            else -> {}
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            // Go back inside the web page first.
            wv.goBack()
        } else {
            // No web history left: confirm a login if there is one, otherwise leave.
            viewModel.onBack(currentCookies())
        }
    }

    LoginContent(isLoading = isPageLoading || loginState == LoginState.Confirming) {
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
                            isPageLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isPageLoading = false
                            // Left the login form: the user may have logged in. The ViewModel
                            // confirms it via /api/v4/user/me/ (deduplicated, one-shot).
                            if (url != null && !url.contains("/accounts/login")) {
                                viewModel.onPageFinished(currentCookies())
                            }
                        }
                    }

                    webViewRef = this
                    loadUrl(LOGIN_URL)
                }
            },
            onRelease = { webView ->
                webViewRef = null
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Stateless login chrome (header + progress). The WebView is passed as [webContent]
 * so that it can be replaced with a placeholder in screenshot tests.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginContent(
    isLoading: Boolean,
    webContent: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "登入 StreetVoice（完成後請按返回鍵）",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = Dimens.SafeHorizontal,
                end = Dimens.SafeHorizontal,
                top = Dimens.SafeVertical,
                bottom = 12.dp,
            ),
        )

        if (isLoading) {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SafeHorizontal),
            )
        }

        webContent()
    }
}

private fun currentCookies(): String? =
    try {
        CookieManager.getInstance().getCookie(BASE_URL)
    } catch (e: Exception) {
        null
    }
