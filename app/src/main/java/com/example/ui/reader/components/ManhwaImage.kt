package com.example.ui.reader.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.net.URL
import java.net.URLEncoder
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

@Composable
fun ManhwaImage(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var localUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                // Determine a safe cache file
                val cacheDir = File(context.cacheDir, "manhwa_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val safeFileName = url.hashCode().toString() + ".jpg"
                val file = File(cacheDir, safeFileName)

                if (!file.exists() || file.length() == 0L) {
                    val client = OkHttpClient.Builder()
                        .addInterceptor(object : Interceptor {
                            override fun intercept(chain: Interceptor.Chain): Response {
                                val request = chain.request()
                                val requestUrl = request.url.toString()
                                
                                if (requestUrl.startsWith("https://anime-aggregator.njirlah.workers.dev")) {
                                    return chain.proceed(request)
                                }
                                
                                val host = try { URL(requestUrl).host } catch (e: Exception) { "" }
                                
                                val builder = request.newBuilder()
                                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                
                                if (host.isNotEmpty()) {
                                    builder.addHeader("Referer", "https://${host}/")
                                    val cookie = android.webkit.CookieManager.getInstance().getCookie("https://${host}")
                                    if (cookie != null) builder.addHeader("Cookie", cookie)
                                }
                                
                                val response = chain.proceed(builder.build())
                                
                                val contentType = response.header("Content-Type") ?: ""
                                if (response.code == 403 || response.code == 503 || contentType.contains("text/html")) {
                                    response.close()
                                    val workerUrl = "https://anime-aggregator.njirlah.workers.dev/?url=" + URLEncoder.encode(requestUrl, "UTF-8")
                                    val workerRequest = request.newBuilder()
                                        .url(workerUrl)
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                        .header("Authorization", "Bearer cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4")
                                        .build()
                                    return chain.proceed(workerRequest)
                                }
                                return response
                            }
                        }).build()

                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful && response.body != null) {
                        file.outputStream().use { out ->
                            response.body!!.byteStream().copyTo(out)
                        }
                    } else {
                        throw Exception("Failed to fetch")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    localUri = Uri.fromFile(file)
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isError = true
                    isLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else if (isError) {
            Text("Gagal memuat gambar", color = MaterialTheme.colorScheme.error)
        } else if (localUri != null) {
            AndroidView(
                factory = { ctx ->
                    SubsamplingScaleImageView(ctx).apply {
                        setMinimumTileDpi(160)
                        setDoubleTapZoomScale(2f)
                        setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
                        // This allows touch events to bubble up for scrolling when zoomed out completely
                        setEagerLoadingEnabled(true)
                        
                        // Disable zoom/pan to let the LazyColumn handle scrolling
                        isPanEnabled = false
                        isZoomEnabled = false
                    }
                },
                update = { view ->
                    if (!view.isReady) {
                        view.setImage(ImageSource.uri(localUri!!))
                        view.setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                            override fun onReady() {
                                val sWidth = view.sWidth.toFloat()
                                val sHeight = view.sHeight.toFloat()
                                if (sWidth > 0 && sHeight > 0) {
                                    val scale = view.width.toFloat() / sWidth
                                    val finalHeight = (sHeight * scale).toInt()
                                    view.layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        finalHeight
                                    )
                                    view.requestLayout()
                                    view.setScaleAndCenter(scale, android.graphics.PointF(sWidth / 2f, sHeight / 2f))
                                }
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
