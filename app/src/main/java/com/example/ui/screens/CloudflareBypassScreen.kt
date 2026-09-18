package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.NetworkModule
import com.example.util.WebViewHelper

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareBypassScreen(
    url: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloudflare Bypass") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    WebViewHelper.configureSecureSettings(this, NetworkModule.userAgent, blockImages = false)

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val targetUri = request?.url ?: return true
                            val scheme = targetUri.scheme?.lowercase() ?: ""
                            return scheme != "http" && scheme != "https"
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, targetUrl: String?): Boolean {
                            if (targetUrl == null) return true
                            val lower = targetUrl.lowercase()
                            return !lower.startsWith("http://") && !lower.startsWith("https://")
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val reqUri = request?.url ?: return super.shouldInterceptRequest(view, request)
                            val scheme = reqUri.scheme?.lowercase() ?: ""
                            if (scheme == "file" || scheme == "content") {
                                return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            WebViewHelper.safeDestroy(view)
                            onBack()
                            return true
                        }
                        
                        override fun onPageFinished(view: WebView?, currentUrl: String?) {
                            super.onPageFinished(view, currentUrl)
                            view?.evaluateJavascript(
                                "(function() { return document.documentElement.outerHTML; })();"
                            ) { html ->
                                if (html != null && !html.contains("cf-challenge") && !html.contains("Just a moment...")) {
                                    CookieManager.getInstance().flush()
                                    onSuccess()
                                }
                            }
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
            },
            modifier = Modifier.padding(padding).fillMaxSize()
        )
    }
}
