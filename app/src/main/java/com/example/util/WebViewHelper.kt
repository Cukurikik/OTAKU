package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import com.example.data.NetworkModule

object WebViewHelper {

    /**
     * Safely and cleanly destroys a WebView instance on the Main Thread.
     * Prevents WebKit renderer process leaks and crashes (aw_browser_terminator code -1).
     */
    fun safeDestroy(webView: WebView?) {
        if (webView == null) return
        val cleanup = Runnable {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                try {
                    webView.removeJavascriptInterface("AndroidBridge")
                    webView.removeJavascriptInterface("android")
                    webView.removeJavascriptInterface("WebAppInterface")
                } catch (_: Throwable) {}
                webView.webViewClient = android.webkit.WebViewClient()
                webView.webChromeClient = null
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Throwable) {}
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run()
        } else {
            Handler(Looper.getMainLooper()).post(cleanup)
        }
    }

    /**
     * Resets a WebView instance without destroying it so it can be safely reused.
     */
    fun resetInstance(webView: WebView?) {
        if (webView == null) return
        val reset = Runnable {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                try {
                    webView.removeJavascriptInterface("AndroidBridge")
                    webView.removeJavascriptInterface("android")
                    webView.removeJavascriptInterface("WebAppInterface")
                } catch (_: Throwable) {}
                webView.webViewClient = android.webkit.WebViewClient()
                webView.webChromeClient = null
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
            } catch (_: Throwable) {}
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            reset.run()
        } else {
            Handler(Looper.getMainLooper()).post(reset)
        }
    }

    /**
     * Applies hardened, crash-resistant settings for WebView instances.
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun configureSecureSettings(
        webView: WebView,
        userAgent: String = NetworkModule.userAgent,
        blockImages: Boolean = true,
        softwareRendering: Boolean = true
    ) {
        if (softwareRendering) {
            try {
                // Disable hardware acceleration / GPU rendering on headless/background WebViews.
                // Prevents Mesa GPU render node failures and Chromium GPU process crashes (aw_browser_terminator -1).
                webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            } catch (_: Throwable) {}
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            userAgentString = userAgent
            
            if (blockImages) {
                loadsImagesAutomatically = false
                blockNetworkImage = true
            }

            // WebKit Security Hardening
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }
    }
}
