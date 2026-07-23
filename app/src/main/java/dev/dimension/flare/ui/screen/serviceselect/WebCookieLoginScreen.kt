package dev.dimension.flare.ui.screen.serviceselect

import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.ui.component.FlareScaffold
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

@Composable
internal fun WebCookieLoginScreen(
    url: String,
    callback: (String?) -> Boolean,
    onBack: () -> Unit,
) {
    val webViewState = rememberWebViewState(url)
    // 只当 URL 从登录页跳走后才认为可能已登录，防止 Laravel 的 session cookie 误判
    LaunchedEffect(url) {
        // 先等一段时间让页面加载
        delay(3.seconds)
        while (true) {
            webViewState.lastLoadedUrl?.let { loadedUrl ->
                // URL 没变（还在登录页上）→ 跳过，等用户登录后跳转
                if (loadedUrl == url) {
                    delay(2.seconds)
                    return@let
                }
                val cookies =
                    CookieManager
                        .getInstance()
                        .getCookie(loadedUrl)
                if (callback(cookies)) {
                    onBack()
                    break
                }
            }
            delay(2.seconds)
        }
    }
    FlareScaffold {
        WebView(
            webViewState,
            layoutParams =
                FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            modifier =
                Modifier
                    .alpha(0.99f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(it)
                    .fillMaxSize(),
            onCreated = {
                val originalClient = it.webViewClient
                it.webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        // 拦截非 http/https 协议（如 snssdk143://），防止 ERR_UNKNOWN_URL_SCHEME
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            return true
                        }
                        return originalClient?.shouldOverrideUrlLoading(view, request) ?: false
                    }
                }
                with(it.settings) {
                    userAgentString = USER_AGENT
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(it, true)
                // 清除该域名所有旧 Cookie，防止旧 session 导致 canResume 误判为已登录
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
            },
        )
    }
}
