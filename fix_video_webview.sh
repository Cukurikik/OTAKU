#!/bin/bash
cat << 'INNER' > app/src/main/java/com/example/data/sources/VideoExtractorEngine.kt
package com.example.data.sources

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class VideoExtractorEngine(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractVideo(
        episodeUrl: String, 
        episodeId: String = episodeUrl,
        timeoutMs: Long = 15000L
    ): VideoStreamInfo? = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            suspendCancellableCoroutine { continuation ->
                webView = WebView(context)
                webView!!.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                }

                val isCompleted = AtomicBoolean(false)

                fun completeWith(url: String?) {
                    if (isCompleted.compareAndSet(false, true)) {
                        if (url != null) {
                            // Launch parsing on IO
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                val streamInfo = parseVideoUrl(url, episodeId)
                                withContext(Dispatchers.Main) {
                                    if (continuation.isActive) {
                                        continuation.resume(streamInfo)
                                    }
                                }
                            }
                        } else {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                }

                webView!!.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        
                        // Skip ads and tracking
                        val lowerUrl = reqUrl.lowercase()
                        if (lowerUrl.contains("google-analytics") || 
                            lowerUrl.contains("doubleclick") ||
                            lowerUrl.contains("adsystem") ||
                            lowerUrl.contains("exdynsrv")) {
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }

                        // Look for m3u8 or mp4
                        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains(".mpd")) {
                            // Skip audio only or irrelevant
                            if (!lowerUrl.contains("audio") && !lowerUrl.contains("googlevideo.com/videoplayback?id=")) {
                               completeWith(reqUrl)
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        completeWith(null)
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("""
                            (function() {
                                // Attempt to click play buttons if there are any overlays
                                var playBtn = document.querySelector('.play-button, .jw-icon-display, .vjs-big-play-button, #play');
                                if (playBtn) playBtn.click();
                                
                                // Look for raw video tags
                                var v = document.querySelector('video source, video');
                                if (v && v.src) return v.src;
                                if (v && v.querySelector('source')) return v.querySelector('source').src;
                                return null;
                            })();
                        """.trimIndent()) { result ->
                            val resUrl = result?.replace("\"", "")
                            if (!resUrl.isNullOrEmpty() && resUrl != "null" && (resUrl.contains(".m3u8") || resUrl.contains(".mp4"))) {
                                completeWith(resUrl)
                            }
                        }
                    }
                }

                webView!!.loadUrl(episodeUrl)

                // Timeout mechanism
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    delay(timeoutMs)
                    if (!isCompleted.get()) {
                        completeWith(null)
                    }
                }
            }
        } finally {
            webView?.destroy()
        }
    }

    private suspend fun parseVideoUrl(url: String, episodeId: String): VideoStreamInfo {
        // If it's MP4, return directly
        if (url.contains(".mp4")) {
            return VideoStreamInfo(
                episodeId = episodeId,
                embedUrl = url,
                qualities = listOf(VideoQuality("Lokal/Raw", url))
            )
        }

        // If it's M3U8, try to parse master playlist to get qualities
        if (url.contains(".m3u8")) {
            try {
                val okHttpClient = com.example.data.NetworkModule.okHttpClient
                val request = okhttp3.Request.Builder()
                    .url(url)
                    // .header("Referer", ...) if needed
                    .build()
                
                val response = kotlinx.coroutines.withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val qualities = parseM3u8Qualities(body, url)
                    if (qualities.isNotEmpty()) {
                        return VideoStreamInfo(
                            episodeId = episodeId,
                            embedUrl = url,
                            qualities = qualities
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback
        return VideoStreamInfo(
            episodeId = episodeId,
            embedUrl = url,
            qualities = listOf(VideoQuality(if (url.contains(".m3u8")) "Auto" else "Lokal/Raw", url))
        )
    }

    private fun parseM3u8Qualities(masterPlaylist: String, masterUrl: String): List<VideoQuality> {
        val qualities = mutableListOf<VideoQuality>()
        val lines = masterPlaylist.split("\n")
        var currentResolution = "Auto"
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                // Example: #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
                val resMatch = Regex("RESOLUTION=\\d+x(\\d+)").find(line)
                if (resMatch != null) {
                    currentResolution = "${resMatch.groupValues[1]}p"
                }
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val streamUrl = if (line.startsWith("http")) {
                    line
                } else {
                    // It's a relative URL, construct the absolute URL
                    val basePath = masterUrl.substringBeforeLast("/")
                    "$basePath/$line"
                }
                qualities.add(VideoQuality(currentResolution, streamUrl))
                currentResolution = "Auto"
            }
        }
        
        return qualities
    }
}
INNER
