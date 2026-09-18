package com.example.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.WebViewHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerOAuthDialog(
    authUrl: String,
    redirectUri: String,
    onTokenExtracted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Login") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { padding ->
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        WebViewHelper.configureSecureSettings(this, "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36", blockImages = false)
                        webViewClient = object : WebViewClient() {
                            @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                WebViewHelper.safeDestroy(view)
                                onDismiss()
                                return true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith(redirectUri)) {
                                    // Parse access token
                                    val uri = Uri.parse(url.replace("#", "?"))
                                    val token = uri.getQueryParameter("access_token")
                                    if (token != null) {
                                        onTokenExtracted(token)
                                        return true
                                    }
                                }
                                return false
                            }
                        }
                        loadUrl(authUrl)
                    }
                },
                onRelease = { webView ->
                    WebViewHelper.safeDestroy(webView)
                }
            )
        }
    }
}
