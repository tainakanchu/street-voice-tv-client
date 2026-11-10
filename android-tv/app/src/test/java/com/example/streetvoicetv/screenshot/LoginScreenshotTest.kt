package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import com.example.streetvoicetv.ui.login.LoginContent

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class LoginScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    @Test
    fun login_loading() = compose.captureTv("login_loading") {
        LoginContent(isLoading = true) { WebViewPlaceholder() }
    }

    @Test
    fun login_loaded() = compose.captureTv("login_loaded") {
        LoginContent(isLoading = false) { WebViewPlaceholder() }
    }

    /** Stand-in for the login WebView (WebView cannot render under Robolectric). */
    @Composable
    private fun WebViewPlaceholder() {
        Box(
            Modifier.fillMaxSize().background(Color(0xFFEEEEEE)),
            contentAlignment = Alignment.Center,
        ) {
            Text("WebView: https://streetvoice.com/accounts/login/", color = Color.DarkGray)
        }
    }
}
