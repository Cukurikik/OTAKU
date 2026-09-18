import re

with open("app/src/main/java/com/example/data/sources/WebViewFetcher.kt", "r") as f:
    code = f.read()

# Add interceptVideoUrl method
intercept_method = """
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun interceptVideoUrl(url: String, waitDelayMs: Long = 15000): String = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            suspendCancellableCoroutine { continuation ->
                webView = WebView(context)
                webView!!.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    userAgentString = NetworkModule.userAgent
                }

                var finished = false
                webView!!.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        val lowerUrl = reqUrl.lowercase()
                        
                        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains(".webm")) {
                            if (!finished && continuation.isActive) {
                                finished = true
                                continuation.resume(reqUrl)
                            }
                        }
                        
                        if (lowerUrl.contains("google-analytics") || 
                            lowerUrl.contains("doubleclick") || 
                            lowerUrl.contains("facebook.net") ||
                            lowerUrl.contains("disqus.com")) {
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        if (!finished && continuation.isActive) {
                            finished = true
                            continuation.resumeWithException(Exception("WebView renderer crashed"))
                        }
                        return true
                    }
                }

                webView!!.loadUrl(url)

                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    delay(waitDelayMs)
                    if (!finished && continuation.isActive) {
                        finished = true
                        continuation.resume(url) // Fallback to original url
                    }
                }
            }
        } finally {
            webView?.destroy()
        }
    }
"""

if "fun interceptVideoUrl" not in code:
    code = code[:code.rfind("}")] + intercept_method + "\n}\n"
    
with open("app/src/main/java/com/example/data/sources/WebViewFetcher.kt", "w") as f:
    f.write(code)
