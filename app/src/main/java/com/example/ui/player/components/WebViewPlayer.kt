package com.example.ui.player.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.NetworkModule
import com.example.util.WebViewHelper

/**
 * Fallback WebView video player for embed sources that require iframe/browser execution.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayer(
    url: String,
    modifier: Modifier = Modifier
) {
    var webViewKey by remember { mutableIntStateOf(0) }

    key(webViewKey) {
        AndroidView(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    WebViewHelper.configureSecureSettings(this, NetworkModule.userAgent, blockImages = false)
                    settings.mediaPlaybackRequiresUserGesture = false

                    setBackgroundColor(android.graphics.Color.BLACK)
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return true
                            val scheme = uri.scheme?.lowercase() ?: ""
                            return scheme != "http" && scheme != "https"
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, targetUrl: String?): Boolean {
                            if (targetUrl == null) return true
                            val lower = targetUrl.lowercase()
                            return !lower.startsWith("http://") && !lower.startsWith("https://")
                        }

                        @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            WebViewHelper.safeDestroy(view)
                            webViewKey++
                            return true
                        }
                    }

                    loadUrl(url)
                }
            },
            update = { webView ->
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            },
            onRelease = { webView ->
                WebViewHelper.safeDestroy(webView)
            }
        )
    }
}
